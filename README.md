# Pokemon Card Price Checker

A database-driven console application for searching Pokemon card market prices, built for the CS1104 Semester Project. Data is sourced from [PriceCharting.com](https://www.pricecharting.com).

## Quick start (VS Code) — for receiving this as a zip

1. Unzip the folder
2. Open **Visual Studio Code**
3. **File → Open Folder** → select the `pokemon-price-checker` folder
4. Install **Extension Pack for Java** when prompted
5. Press **F5** (or Run → Start Debugging)
6. Type menu choices in the **Terminal** panel at the bottom

See **HOW_TO_RUN.txt** for full step-by-step instructions and troubleshooting.

## Send this project to a friend

From inside the project folder, run:

```bash
chmod +x package.sh
./package.sh
```

This creates `pokemon-price-checker.zip` in the parent folder, with everything included (source, SQLite library, VS Code config, sample data). Your friend unzips it and follows the steps above.

## Features

- **Search** cards by name with optional set and language filters
- **Partial, case-insensitive** name matching (`LIKE` with `COLLATE NOCASE`)
- **Condition-specific pricing** — Near Mint, Lightly Played, Moderately Played, Heavily Played
- **Sort by price** descending so highest-value matches appear first
- **Admin tools** to insert, update, and delete card records
- **Personal collection** — save cards across sessions, add and remove from your collection
- **Add to collection** after searching for a card
- **Custom SELECT** query runner

## What's included (no extra downloads needed)

| Folder / file | Purpose |
|---------------|---------|
| `src/` | Java source code |
| `lib/sqlite-jdbc-3.51.2.0.jar` | SQLite database driver (bundled) |
| `sql/` | Database schema and seed card data |
| `cards.db` | Pre-loaded database and saved collection (persists between sessions) |
| `.vscode/` | VS Code run configuration (press F5 to start) |
| `HOW_TO_RUN.txt` | Instructions for your friend |

## Run without VS Code

**Mac / Linux:** `./run.sh`  
**Windows:** double-click `run.bat`

Requires Java 11+ installed ([Adoptium JDK](https://adoptium.net/) is free).

## Sync live data from PriceCharting (optional)

```bash
python3 scripts/sync_pricecharting.py
```

Set your API token for full price data:

```bash
export PRICECHARTING_API_TOKEN=your_token   # Mac/Linux
set PRICECHARTING_API_TOKEN=your_token      # Windows
python3 scripts/sync_pricecharting.py
```

## Database Schema

See `sql/schema.sql`:

| Column     | Type    | Constraints                          |
|------------|---------|--------------------------------------|
| id         | INTEGER | PRIMARY KEY AUTOINCREMENT            |
| name       | TEXT    | NOT NULL, COLLATE NOCASE             |
| set_name   | TEXT    | NOT NULL                             |
| language   | TEXT    | NOT NULL                             |
| condition  | TEXT    | NOT NULL, CHECK (4 valid conditions) |
| price      | REAL    | NOT NULL, CHECK (price >= 0)         |
| product_id | TEXT    | PriceCharting product ID             |

## Example SQL Queries

```sql
SELECT name, set_name, language, condition, price
FROM cards
WHERE name LIKE '%pikachu%' COLLATE NOCASE
ORDER BY price DESC;
```
