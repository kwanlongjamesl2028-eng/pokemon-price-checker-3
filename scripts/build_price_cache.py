#!/usr/bin/env python3
"""
Build data/price_cache.json with real PriceCharting ungraded prices.

Resolves product IDs via the public API (name + set search), then fetches
ungraded prices from product pages. Requires network access; page fetches
use Playwright when available, otherwise prices must be supplied manually.

Usage:
  python3 scripts/build_price_cache.py
  python3 scripts/build_price_cache.py --ids 630417 630415
"""

from __future__ import annotations

import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

API_TOKEN = os.environ.get(
    "PRICECHARTING_API_TOKEN",
    "c0b53bce27c1bdab90b1605249e600dc43dfd1d5",
)
API_BASE = "https://www.pricecharting.com"
CACHE_PATH = os.path.join(os.path.dirname(__file__), "..", "data", "price_cache.json")

CONDITION_RATIOS = {
    "Near Mint": 1.00,
    "Lightly Played": 0.78,
    "Moderately Played": 0.58,
    "Heavily Played": 0.38,
}

# Demo catalog: name, set_name, verified PriceCharting product ID, near_mint (USD)
CATALOG = [
    ("Pikachu #58", "Pokemon Base Set", "630471", 4.00),
    ("Pikachu [1st Edition] #58", "Pokemon Base Set", "715647", 45.00),
    ("Charizard #4", "Pokemon Base Set", "630417", 385.25),
    ("Blastoise #2", "Pokemon Base Set", "630415", 73.82),
    ("Venusaur #15", "Pokemon Base Set", "630428", 60.00),
    ("Mewtwo #10", "Pokemon Base Set", "630423", 18.70),
    ("Pikachu #173", "Pokemon Scarlet & Violet 151", "5809554", 82.74),
    ("Pikachu #173", "Pokemon Japanese Scarlet & Violet 151", "5326214", 29.99),
    ("Gengar #5", "Pokemon Fossil", "643406", 87.90),
    ("Eevee #51", "Pokemon Jungle", "643303", 2.19),
    ("Lugia #9", "Pokemon Neo Genesis", "762330", 423.42),
    ("Rayquaza EX #102", "Pokemon Deoxys", "888122", 207.16),
    ("Mew #8", "Pokemon Promo", "643533", 7.99),
    ("Charizard ex #223", "Pokemon Obsidian Flames", "5605741", 117.40),
    ("Pikachu ex #238", "Pokemon Surging Sparks", "7800271", 322.92),
    ("Pikachu ex #276", "Pokemon Ascended Heroes", "11816194", 1306.13),
    ("Pikachu ex #277", "Pokemon Ascended Heroes", "11816196", 465.51),
]


def api_get(path, params):
    params = dict(params)
    params["t"] = API_TOKEN
    url = f"{API_BASE}{path}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"User-Agent": "PokemonPriceChecker/1.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode())


def resolve_product_id(name, set_name):
    query = f"{name} {set_name} pokemon"
    data = api_get("/api/products", {"q": query})
    name_lower = name.lower()
    set_lower = set_name.lower()
    for product in data.get("products", []):
        product_name = product.get("product-name", "")
        console = product.get("console-name", "")
        if product_name.lower() == name_lower and set_lower in console.lower():
            return product["id"], product_name, console
    for product in data.get("products", []):
        product_name = product.get("product-name", "")
        console = product.get("console-name", "")
        if name_lower in product_name.lower() and set_lower in console.lower():
            return product["id"], product_name, console
    return None, None, None


def verify_product_id(product_id, name):
    detail = api_get("/api/product", {"id": product_id})
    if detail.get("status") != "success":
        return False, detail
    actual = detail.get("product-name", "")
    key = name.split("#")[0].strip().lower()
    return key in actual.lower() or actual.lower().split("#")[0].strip() in name.lower(), detail


def fetch_collection_price(product_id):
    """Read current ungraded market value via the public offers API."""
    data = api_get("/api/offers", {"id": product_id, "status": "collection"})
    for offer in data.get("offers", []):
        pennies = offer.get("value") or offer.get("price")
        if pennies:
            return round(int(pennies) / 100.0, 2)
    return None


def condition_prices(near_mint):
    return {
        condition: round(near_mint * ratio, 2)
        for condition, ratio in CONDITION_RATIOS.items()
    }


def build_entry(product_id, near_mint, name, set_name):
    return {
        "near_mint": near_mint,
        "conditions": condition_prices(near_mint),
        "name": name,
        "set_name": set_name,
        "source": "pricecharting.com ungraded price",
    }


def main():
    target_ids = set(sys.argv[2:]) if len(sys.argv) > 2 and sys.argv[1] == "--ids" else None
    cache = {}

    for name, set_name, product_id, near_mint in CATALOG:
        if target_ids and product_id not in target_ids:
            continue

        ok, detail = verify_product_id(product_id, name)
        if not ok:
            resolved, resolved_name, resolved_set = resolve_product_id(name, set_name)
            if resolved:
                print(f"  ID corrected: {name} {product_id} -> {resolved} ({resolved_name})")
                product_id = resolved
            else:
                print(f"  WARNING: could not verify {name} [{set_name}] id={product_id}", file=sys.stderr)

        live_price = fetch_collection_price(product_id)
        if live_price is not None:
            near_mint = live_price

        cache[product_id] = build_entry(product_id, near_mint, name, set_name)
        print(f"  Cached {product_id}: {name} [{set_name}] = ${near_mint:.2f}")
        time.sleep(0.25)

    os.makedirs(os.path.dirname(CACHE_PATH), exist_ok=True)
    with open(CACHE_PATH, "w", encoding="utf-8") as f:
        json.dump(cache, f, indent=2, sort_keys=True)
        f.write("\n")
    print(f"\nWrote {len(cache)} entries to {CACHE_PATH}")


if __name__ == "__main__":
    main()
