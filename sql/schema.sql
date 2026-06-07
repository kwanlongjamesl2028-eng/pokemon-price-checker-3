-- Pokemon Card Price Checker - Database Schema
-- Matches CS1104 semester project design specifications

CREATE TABLE IF NOT EXISTS cards (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL COLLATE NOCASE,
    set_name    TEXT    NOT NULL,
    language    TEXT    NOT NULL,
    condition   TEXT    NOT NULL
                        CHECK (condition IN (
                            'Near Mint',
                            'Lightly Played',
                            'Moderately Played',
                            'Heavily Played'
                        )),
    price       REAL    NOT NULL CHECK (price >= 0),
    product_id  TEXT,
    UNIQUE (name, set_name, language, condition)
);

CREATE INDEX IF NOT EXISTS idx_cards_name ON cards (name);

CREATE TABLE IF NOT EXISTS collection (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    card_id     INTEGER NOT NULL UNIQUE,
    added_at    TEXT    NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (card_id) REFERENCES cards (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_collection_card_id ON collection (card_id);
