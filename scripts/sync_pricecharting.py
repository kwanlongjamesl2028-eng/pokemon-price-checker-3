#!/usr/bin/env python3
"""
Sync Pokemon card data from PriceCharting.com into the local SQLite database.

Uses the PriceCharting REST API (https://www.pricecharting.com/api-documentation)
to search for products and store condition-specific prices.

Set PRICECHARTING_API_TOKEN for full price data (Legendary subscription).
The public demo token returns product metadata only; condition prices are
estimated from the ungraded (loose) price when available.
"""

import json
import os
import sqlite3
import time
import urllib.error
import urllib.parse
import urllib.request

DB_PATH = os.path.join(os.path.dirname(__file__), "..", "cards.db")
SCHEMA_PATH = os.path.join(os.path.dirname(__file__), "..", "sql", "schema.sql")
API_BASE = "https://www.pricecharting.com"
API_TOKEN = os.environ.get(
    "PRICECHARTING_API_TOKEN",
    "c0b53bce27c1bdab90b1605249e600dc43dfd1d5",
)

CONDITIONS = [
    "Near Mint",
    "Lightly Played",
    "Moderately Played",
    "Heavily Played",
]

# Typical TCGPlayer-style ratios relative to Near Mint ungraded price
CONDITION_RATIOS = {
    "Near Mint": 1.00,
    "Lightly Played": 0.78,
    "Moderately Played": 0.58,
    "Heavily Played": 0.38,
}

SEARCH_QUERIES = [
    "pikachu pokemon",
    "charizard pokemon",
    "mewtwo pokemon",
    "blastoise pokemon",
    "venusaur pokemon",
    "mew pokemon",
    "lugia pokemon",
    "rayquaza pokemon",
    "gengar pokemon",
    "eevee pokemon",
]


def api_get(path, params):
    """Call the PriceCharting API and return parsed JSON."""
    params = dict(params)
    params["t"] = API_TOKEN
    url = f"{API_BASE}{path}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"User-Agent": "PokemonPriceChecker/1.0"})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode())


def infer_language(console_name):
    """Derive card language from PriceCharting console/set name."""
    lower = console_name.lower()
    if "japanese" in lower:
        return "Japanese"
    if "chinese" in lower:
        return "Chinese"
    if "korean" in lower:
        return "Korean"
    return "English"


def pennies_to_dollars(pennies):
    if pennies is None or pennies == "":
        return None
    return round(int(pennies) / 100.0, 2)


def condition_prices_from_product(product):
    """
    Build condition-specific prices from a PriceCharting product response.

    PriceCharting stores card prices in grading tiers; we map the ungraded
    (loose) price to Near Mint and derive other play conditions using
    standard market ratios used across TCG price guides.
    """
    loose = product.get("loose-price")
    if loose is not None and loose != "":
        base = pennies_to_dollars(loose)
        return {
            cond: round(base * CONDITION_RATIOS[cond], 2)
            for cond in CONDITIONS
        }

    # Fallback: use any available graded price field as a rough base
    for key in ("new-price", "cib-price", "graded-price", "manual-only-price"):
        val = product.get(key)
        if val is not None and val != "":
            base = pennies_to_dollars(val) * 0.6
            return {
                cond: round(base * CONDITION_RATIOS[cond], 2)
                for cond in CONDITIONS
            }
    return None


def init_db(conn):
    """Create schema if it does not exist."""
    with open(SCHEMA_PATH, encoding="utf-8") as f:
        conn.executescript(f.read())


def upsert_card(conn, name, set_name, language, condition, price, product_id):
    """Insert or replace a card row using SQL."""
    conn.execute(
        """
        INSERT INTO cards (name, set_name, language, condition, price, product_id)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT(name, set_name, language, condition)
        DO UPDATE SET price = excluded.price, product_id = excluded.product_id
        """,
        (name, set_name, language, condition, price, product_id),
    )


def sync_query(conn, query, max_products=5):
    """Search PriceCharting and store matching cards."""
    print(f"Searching PriceCharting for: {query!r}")
    try:
        data = api_get("/api/products", {"q": query})
    except urllib.error.HTTPError as e:
        print(f"  API error: {e}")
        return 0

    if data.get("status") != "success":
        print(f"  Search failed: {data.get('error-message', 'unknown error')}")
        return 0

    products = data.get("products", [])[:max_products]
    inserted = 0

    for summary in products:
        product_id = summary["id"]
        time.sleep(1.1)  # respect 1 req/sec API limit

        try:
            detail = api_get("/api/product", {"id": product_id})
        except urllib.error.HTTPError as e:
            print(f"  Skipping {product_id}: {e}")
            continue

        if detail.get("status") != "success":
            continue

        name = detail.get("product-name", summary.get("product-name", ""))
        set_name = detail.get("console-name", summary.get("console-name", ""))
        language = infer_language(set_name)
        prices = condition_prices_from_product(detail)

        if not prices:
            print(f"  No price data for {name} ({set_name}) — skipping")
            continue

        for condition, price in prices.items():
            upsert_card(conn, name, set_name, language, condition, price, product_id)
            inserted += 1

        print(f"  Stored: {name} | {set_name} | {language} | 4 conditions")

    return inserted


def print_stats(conn):
    """Run SQL summary queries against the populated database."""
    cur = conn.cursor()
    cur.execute("SELECT COUNT(*) FROM cards")
    total = cur.fetchone()[0]
    cur.execute("SELECT COUNT(DISTINCT name || set_name || language) FROM cards")
    unique_cards = cur.fetchone()[0]
    print(f"\nDatabase summary: {total} rows, {unique_cards} unique card variants")

    cur.execute(
        """
        SELECT name, set_name, language, condition, price
        FROM cards
        ORDER BY price DESC
        LIMIT 5
        """
    )
    print("\nTop 5 prices (SQL query):")
    for row in cur.fetchall():
        print(f"  ${row[4]:.2f}  {row[0]}  [{row[1]}]  {row[2]}  {row[3]}")


def main():
    conn = sqlite3.connect(DB_PATH)
    init_db(conn)

    total = 0
    for query in SEARCH_QUERIES:
        total += sync_query(conn, query, max_products=3)

    conn.commit()
    print_stats(conn)
    conn.close()
    print(f"\nSync complete. {total} condition rows written to {DB_PATH}")


if __name__ == "__main__":
    main()
