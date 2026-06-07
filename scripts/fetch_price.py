#!/usr/bin/env python3
"""
Fetch real ungraded prices from PriceCharting.com product pages.

The public API token returns product metadata only. This script loads the
product page in a headless browser and reads the embedded chart_data.used
price (Ungraded / Near Mint market value).

Usage:
  python3 scripts/fetch_price.py 11816194
  python3 scripts/fetch_price.py --query "pikachu ascended heroes"
"""

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


def api_get(path, params):
    params = dict(params)
    params["t"] = API_TOKEN
    url = f"{API_BASE}{path}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"User-Agent": "PokemonPriceChecker/1.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode())


def slugify(text):
    slug = re.sub(r"[^a-z0-9]+", "-", text.lower())
    return slug.strip("-")


def build_product_url(console_name, product_name):
    return f"{API_BASE}/game/{slugify(console_name)}/{slugify(product_name)}"


def load_cache():
    if not os.path.exists(CACHE_PATH):
        return {}
    with open(CACHE_PATH, encoding="utf-8") as f:
        return json.load(f)


def save_cache(cache):
    os.makedirs(os.path.dirname(CACHE_PATH), exist_ok=True)
    with open(CACHE_PATH, "w", encoding="utf-8") as f:
        json.dump(cache, f, indent=2, sort_keys=True)


def parse_chart_used_pennies(html):
    match = re.search(r"chart_data\s*=\s*(\{.*?\})\s*;", html, re.S)
    if not match:
        return None
    chart_json = match.group(1)
    used_match = re.search(r'"used"\s*:\s*(\[\[.*?\]\])', chart_json, re.S)
    if not used_match:
        return None
    points = json.loads(used_match.group(1))
    for timestamp, pennies in reversed(points):
        if pennies and pennies > 0:
            return pennies
    return None


def fetch_used_price_from_page(url):
    from playwright.sync_api import sync_playwright

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        page.goto(url, wait_until="domcontentloaded", timeout=90000)
        page.wait_for_timeout(3000)
        pennies = page.evaluate(
            """() => {
                function toPennies(value) {
                    if (value > 10000) return Math.round(value);
                    return Math.round(value * 100);
                }
                if (typeof VGPC !== 'undefined' && VGPC.chart_data && VGPC.chart_data.used) {
                    const points = VGPC.chart_data.used;
                    for (let i = points.length - 1; i >= 0; i--) {
                        if (points[i][1] > 0) return toPennies(points[i][1]);
                    }
                }
                const html = document.documentElement.innerHTML;
                const match = html.match(/chart_data\\s*=\\s*(\\{.*?\\})\\s*;/s);
                if (!match) return null;
                const usedMatch = match[1].match(/"used"\\s*:\\s*(\\[\\[.*?\\]\\])/s);
                if (!usedMatch) return null;
                const points = JSON.parse(usedMatch[1]);
                for (let i = points.length - 1; i >= 0; i--) {
                    if (points[i][1] > 0) return toPennies(points[i][1]);
                }
                return null;
            }"""
        )
        browser.close()
        return pennies


def get_product_detail(product_id):
    data = api_get("/api/product", {"id": product_id})
    if data.get("status") != "success":
        raise RuntimeError(data.get("error-message", "Product lookup failed"))
    return data


def get_ungraded_price(product_id):
    detail = get_product_detail(product_id)
    loose = detail.get("loose-price")
    if loose not in (None, ""):
        return int(loose) / 100.0

    url = build_product_url(detail["console-name"], detail["product-name"])
    pennies = fetch_used_price_from_page(url)
    if pennies is None:
        raise RuntimeError(f"Could not parse price from {url}")
    return round(pennies / 100.0, 2)


def condition_prices(near_mint):
    return {
        condition: round(near_mint * ratio, 2)
        for condition, ratio in CONDITION_RATIOS.items()
    }


def cache_product(product_id):
    near_mint = get_ungraded_price(product_id)
    cache = load_cache()
    cache[str(product_id)] = {
        "near_mint": near_mint,
        "conditions": condition_prices(near_mint),
        "fetched_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    }
    save_cache(cache)
    return cache[str(product_id)]


def search_product_ids(query):
    data = api_get("/api/products", {"q": query + " pokemon"})
    return [p["id"] for p in data.get("products", [])]


def main():
    if len(sys.argv) < 2:
        print("Usage: fetch_price.py <product_id> | --query <search terms>", file=sys.stderr)
        sys.exit(1)

    if sys.argv[1] == "--query":
        query = " ".join(sys.argv[2:])
        ids = search_product_ids(query)
        results = {}
        for product_id in ids[:10]:
            try:
                results[product_id] = cache_product(product_id)
                time.sleep(1)
            except Exception as exc:
                results[product_id] = {"error": str(exc)}
        print(json.dumps(results, indent=2))
        return

    product_id = sys.argv[1]
    result = cache_product(product_id)
    print(json.dumps({"product_id": product_id, **result}, indent=2))


if __name__ == "__main__":
    main()
