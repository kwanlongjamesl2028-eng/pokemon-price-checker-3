-- Seed data for Pokemon Card Price Checker
-- Product IDs and prices verified against PriceCharting.com (June 2026)

-- Remove rows that used incorrect PriceCharting product IDs
DELETE FROM cards WHERE product_id IN (
    '630440', '630438', '630451', '630446',
    '643310', '643326', '643400', '889050',
    '643512', '5670034'
);
DELETE FROM cards WHERE name = 'Gengar #94' AND set_name = 'Pokemon Fossil';
DELETE FROM cards WHERE name = 'Eevee #63' AND set_name = 'Pokemon Jungle';
DELETE FROM cards WHERE name = 'Rayquaza #61' AND set_name = 'Pokemon EX Deoxys';

INSERT OR REPLACE INTO cards (name, set_name, language, condition, price, product_id) VALUES
-- Pikachu #58 - Pokemon Base Set (PriceCharting ID: 630471)
('Pikachu #58', 'Pokemon Base Set', 'English', 'Near Mint', 4.00, '630471'),
('Pikachu #58', 'Pokemon Base Set', 'English', 'Lightly Played', 3.12, '630471'),
('Pikachu #58', 'Pokemon Base Set', 'English', 'Moderately Played', 2.32, '630471'),
('Pikachu #58', 'Pokemon Base Set', 'English', 'Heavily Played', 1.52, '630471'),

-- Pikachu #58 1st Edition - Pokemon Base Set (PriceCharting ID: 715647)
('Pikachu [1st Edition] #58', 'Pokemon Base Set', 'English', 'Near Mint', 45.00, '715647'),
('Pikachu [1st Edition] #58', 'Pokemon Base Set', 'English', 'Lightly Played', 35.10, '715647'),
('Pikachu [1st Edition] #58', 'Pokemon Base Set', 'English', 'Moderately Played', 26.10, '715647'),
('Pikachu [1st Edition] #58', 'Pokemon Base Set', 'English', 'Heavily Played', 17.10, '715647'),

-- Charizard #4 - Pokemon Base Set (PriceCharting ID: 630417)
('Charizard #4', 'Pokemon Base Set', 'English', 'Near Mint', 385.25, '630417'),
('Charizard #4', 'Pokemon Base Set', 'English', 'Lightly Played', 300.50, '630417'),
('Charizard #4', 'Pokemon Base Set', 'English', 'Moderately Played', 223.45, '630417'),
('Charizard #4', 'Pokemon Base Set', 'English', 'Heavily Played', 146.40, '630417'),

-- Blastoise #2 - Pokemon Base Set (PriceCharting ID: 630415)
('Blastoise #2', 'Pokemon Base Set', 'English', 'Near Mint', 73.82, '630415'),
('Blastoise #2', 'Pokemon Base Set', 'English', 'Lightly Played', 57.58, '630415'),
('Blastoise #2', 'Pokemon Base Set', 'English', 'Moderately Played', 42.82, '630415'),
('Blastoise #2', 'Pokemon Base Set', 'English', 'Heavily Played', 28.05, '630415'),

-- Venusaur #15 - Pokemon Base Set (PriceCharting ID: 630428)
('Venusaur #15', 'Pokemon Base Set', 'English', 'Near Mint', 60.00, '630428'),
('Venusaur #15', 'Pokemon Base Set', 'English', 'Lightly Played', 46.80, '630428'),
('Venusaur #15', 'Pokemon Base Set', 'English', 'Moderately Played', 34.80, '630428'),
('Venusaur #15', 'Pokemon Base Set', 'English', 'Heavily Played', 22.80, '630428'),

-- Mewtwo #10 - Pokemon Base Set (PriceCharting ID: 630423)
('Mewtwo #10', 'Pokemon Base Set', 'English', 'Near Mint', 18.70, '630423'),
('Mewtwo #10', 'Pokemon Base Set', 'English', 'Lightly Played', 14.59, '630423'),
('Mewtwo #10', 'Pokemon Base Set', 'English', 'Moderately Played', 10.85, '630423'),
('Mewtwo #10', 'Pokemon Base Set', 'English', 'Heavily Played', 7.11, '630423'),

-- Pikachu #173 - Pokemon Scarlet & Violet 151 (PriceCharting ID: 5809554)
('Pikachu #173', 'Pokemon Scarlet & Violet 151', 'English', 'Near Mint', 82.74, '5809554'),
('Pikachu #173', 'Pokemon Scarlet & Violet 151', 'English', 'Lightly Played', 64.54, '5809554'),
('Pikachu #173', 'Pokemon Scarlet & Violet 151', 'English', 'Moderately Played', 47.99, '5809554'),
('Pikachu #173', 'Pokemon Scarlet & Violet 151', 'English', 'Heavily Played', 31.44, '5809554'),

-- Pikachu #173 - Japanese (PriceCharting ID: 5326214)
('Pikachu #173', 'Pokemon Japanese Scarlet & Violet 151', 'Japanese', 'Near Mint', 29.99, '5326214'),
('Pikachu #173', 'Pokemon Japanese Scarlet & Violet 151', 'Japanese', 'Lightly Played', 23.39, '5326214'),
('Pikachu #173', 'Pokemon Japanese Scarlet & Violet 151', 'Japanese', 'Moderately Played', 17.39, '5326214'),
('Pikachu #173', 'Pokemon Japanese Scarlet & Violet 151', 'Japanese', 'Heavily Played', 11.40, '5326214'),

-- Gengar #5 - Pokemon Fossil (PriceCharting ID: 643406)
('Gengar #5', 'Pokemon Fossil', 'English', 'Near Mint', 87.90, '643406'),
('Gengar #5', 'Pokemon Fossil', 'English', 'Lightly Played', 68.56, '643406'),
('Gengar #5', 'Pokemon Fossil', 'English', 'Moderately Played', 50.98, '643406'),
('Gengar #5', 'Pokemon Fossil', 'English', 'Heavily Played', 33.40, '643406'),

-- Eevee #51 - Pokemon Jungle (PriceCharting ID: 643303)
('Eevee #51', 'Pokemon Jungle', 'English', 'Near Mint', 2.19, '643303'),
('Eevee #51', 'Pokemon Jungle', 'English', 'Lightly Played', 1.71, '643303'),
('Eevee #51', 'Pokemon Jungle', 'English', 'Moderately Played', 1.27, '643303'),
('Eevee #51', 'Pokemon Jungle', 'English', 'Heavily Played', 0.83, '643303'),

-- Lugia #9 - Pokemon Neo Genesis (PriceCharting ID: 762330)
('Lugia #9', 'Pokemon Neo Genesis', 'English', 'Near Mint', 423.42, '762330'),
('Lugia #9', 'Pokemon Neo Genesis', 'English', 'Lightly Played', 330.27, '762330'),
('Lugia #9', 'Pokemon Neo Genesis', 'English', 'Moderately Played', 245.58, '762330'),
('Lugia #9', 'Pokemon Neo Genesis', 'English', 'Heavily Played', 160.90, '762330'),

-- Rayquaza EX #102 - Pokemon Deoxys (PriceCharting ID: 888122)
('Rayquaza EX #102', 'Pokemon Deoxys', 'English', 'Near Mint', 207.16, '888122'),
('Rayquaza EX #102', 'Pokemon Deoxys', 'English', 'Lightly Played', 161.58, '888122'),
('Rayquaza EX #102', 'Pokemon Deoxys', 'English', 'Moderately Played', 120.15, '888122'),
('Rayquaza EX #102', 'Pokemon Deoxys', 'English', 'Heavily Played', 78.72, '888122'),

-- Mew #8 - Pokemon Promo (PriceCharting ID: 643533)
('Mew #8', 'Pokemon Promo', 'English', 'Near Mint', 7.99, '643533'),
('Mew #8', 'Pokemon Promo', 'English', 'Lightly Played', 6.23, '643533'),
('Mew #8', 'Pokemon Promo', 'English', 'Moderately Played', 4.63, '643533'),
('Mew #8', 'Pokemon Promo', 'English', 'Heavily Played', 3.04, '643533'),

-- Charizard ex #223 - Pokemon Obsidian Flames (PriceCharting ID: 5605741)
('Charizard ex #223', 'Pokemon Obsidian Flames', 'English', 'Near Mint', 117.40, '5605741'),
('Charizard ex #223', 'Pokemon Obsidian Flames', 'English', 'Lightly Played', 91.57, '5605741'),
('Charizard ex #223', 'Pokemon Obsidian Flames', 'English', 'Moderately Played', 68.09, '5605741'),
('Charizard ex #223', 'Pokemon Obsidian Flames', 'English', 'Heavily Played', 44.61, '5605741'),

-- Pikachu ex #238 - Pokemon Surging Sparks (PriceCharting ID: 7800271)
('Pikachu ex #238', 'Pokemon Surging Sparks', 'English', 'Near Mint', 322.92, '7800271'),
('Pikachu ex #238', 'Pokemon Surging Sparks', 'English', 'Lightly Played', 251.88, '7800271'),
('Pikachu ex #238', 'Pokemon Surging Sparks', 'English', 'Moderately Played', 187.29, '7800271'),
('Pikachu ex #238', 'Pokemon Surging Sparks', 'English', 'Heavily Played', 122.71, '7800271');
