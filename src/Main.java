import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Pokemon Card Price Checker
 *
 * A database-driven console application that lets users search for Pokemon
 * card prices by name, set, and language. Data is sourced from PriceCharting.com
 * and stored in a local SQLite database. Users can save cards to a persistent
 * personal collection.
 */
public class Main {
    private static final Scanner SCANNER = new Scanner(System.in);
    private static CardRepository repo;
    private static CollectionRepository collection;

    public static void main(String[] args) {
        try {
            DatabaseManager db = new DatabaseManager();
            db.initialize();
            repo = new CardRepository(db.getConnection());
            collection = new CollectionRepository(db.getConnection());

            System.out.println("==============================================");
            System.out.println("  Pokemon Card Price Checker");
            System.out.println("  Data source: PriceCharting.com");
            System.out.println("==============================================");

            boolean running = true;
            while (running) {
                printMenu();
                String choice = SCANNER.nextLine().trim();
                switch (choice) {
                    case "1" -> searchCards();
                    case "2" -> collectionMenu();
                    case "3" -> viewAllCards();
                    case "4" -> runCustomSql();
                    case "5" -> {
                        running = false;
                        System.out.println("Goodbye!");
                    }
                    default -> System.out.println("Choice not recognized.");
                }
            }

            db.close();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void printMenu() {
        System.out.println();
        System.out.println("What would you like to do?");
        System.out.println("1. Search cards by name (with optional set/language filters)");
        System.out.println("2. My collection");
        System.out.println("3. View all cards in database (sorted by price, highest first)");
        System.out.println("4. Run a custom SELECT query");
        System.out.println("5. Quit");
        System.out.print("Enter choice: ");
    }

    /**
     * Search function with partial name matching and optional filters.
     * Displays results and offers to add a card to the collection.
     */
    private static void searchCards() {
        SearchContext context = promptSearchFilters();
        if (context == null) {
            return;
        }

        try {
            SearchResult result = fetchSearchResults(context, false);

            if (result.cards.isEmpty()) {
                System.out.println("No cards found.");
                return;
            }

            printSearchResults(result.cards);
            System.out.println(result.cards.size() + " result(s) found.");
            offerAddToCollection(result, context);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        } catch (IOException | InterruptedException e) {
            System.out.println("Could not reach PriceCharting: " + e.getMessage());
            System.out.println("No cards found.");
        }
    }

    /**
     * Collection submenu: view, add, and remove saved cards.
     */
    private static void collectionMenu() {
        boolean inCollection = true;
        while (inCollection) {
            System.out.println();
            System.out.println("--- My Collection ---");
            try {
                int count = collection.count();
                System.out.println("Saved cards: " + count);
            } catch (SQLException e) {
                System.out.println("Could not load collection count.");
            }
            System.out.println("1. View my collection");
            System.out.println("2. Add a card to my collection");
            System.out.println("3. Remove a card from my collection");
            System.out.println("4. Back to main menu");
            System.out.print("Enter choice: ");

            String choice = SCANNER.nextLine().trim();
            switch (choice) {
                case "1" -> viewCollection();
                case "2" -> addToCollection();
                case "3" -> removeFromCollection();
                case "4" -> inCollection = false;
                default -> System.out.println("Choice not recognized.");
            }
        }
    }

    private static void viewCollection() {
        try {
            List<Card> cards = collection.findAll();
            if (cards.isEmpty()) {
                System.out.println("Your collection is empty.");
                System.out.println("Search for cards (option 1) and add them to your collection.");
                return;
            }
            printCardTable(cards, true);
            double total = cards.stream().mapToDouble(Card::getPrice).sum();
            System.out.printf("Collection total value: $%.2f (%d card(s))%n", total, cards.size());
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    private static void addToCollection() {
        SearchContext context = promptSearchFilters();
        if (context == null) {
            return;
        }

        try {
            SearchResult result = fetchSearchResults(context, true);

            if (result.cards.isEmpty()) {
                System.out.println("No cards found.");
                return;
            }

            printCardTable(result.cards, true);
            offerAddToCollection(result, context);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        } catch (IOException | InterruptedException e) {
            System.out.println("Could not reach PriceCharting: " + e.getMessage());
        }
    }

    private static void removeFromCollection() {
        System.out.print("Enter card ID to remove from your collection: ");
        String idStr = SCANNER.nextLine().trim();
        if (idStr.isEmpty()) {
            System.out.println("Please enter a valid ID.");
            return;
        }

        try {
            int cardId = Integer.parseInt(idStr);
            int removed = collection.remove(cardId);
            if (removed == 0) {
                System.out.println("That card is not in your collection.");
            } else {
                System.out.println("Card removed from your collection.");
            }
        } catch (NumberFormatException e) {
            System.out.println("ID must be a valid number.");
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    private static void viewAllCards() {
        try {
            List<Card> cards = repo.findAll();
            if (cards.isEmpty()) {
                System.out.println("No cards in database.");
                return;
            }
            printCardTable(cards, true);
            System.out.println(cards.size() + " total rows.");
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    private static void runCustomSql() {
        System.out.print("Enter SQL SELECT query (one line): ");
        String sql = SCANNER.nextLine();

        try {
            System.out.println("\nRunning SQL: " + sql + "\n");
            repo.runQueryAndPrint(sql);
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        } catch (SQLException e) {
            System.out.println("SQL error: " + e.getMessage());
        }
    }

    private static SearchContext promptSearchFilters() {
        System.out.print("Enter card name (required): ");
        String name = SCANNER.nextLine();
        if (name.trim().isEmpty()) {
            System.out.println("Please enter a valid name");
            return null;
        }

        System.out.print("Enter set filter (optional, press Enter to skip): ");
        String setFilter = SCANNER.nextLine();

        System.out.print("Enter language filter (optional, press Enter to skip): ");
        String languageFilter = SCANNER.nextLine();

        return new SearchContext(name, setFilter, languageFilter);
    }

    /**
     * Fetch matching cards from the local database or PriceCharting.com.
     */
    private static SearchResult fetchSearchResults(SearchContext context, boolean saveToDatabase)
            throws SQLException, IOException, InterruptedException {
        List<Card> results = repo.search(context.name, context.setFilter, context.languageFilter);
        if (!results.isEmpty()) {
            PriceService prices = new PriceService();
            List<Card> refreshed = prices.withCurrentPrices(results);
            if (saveToDatabase) {
                repo.importFromPriceCharting(toImports(refreshed));
            }
            return new SearchResult(refreshed, List.of(), false, true);
        }

        System.out.println("Searching PriceCharting.com...");
        PriceChartingClient client = new PriceChartingClient();
        List<PriceChartingClient.CardImport> fetched = client.fetchCards(
                context.name, context.setFilter, context.languageFilter);
        if (fetched.isEmpty()) {
            return new SearchResult(List.of(), List.of(), false, false);
        }

        if (saveToDatabase) {
            repo.importFromPriceCharting(fetched);
            List<Card> saved = repo.search(context.name, context.setFilter, context.languageFilter);
            return new SearchResult(saved, fetched, true, true);
        }

        List<Card> displayResults = cardsFromImports(fetched);
        return new SearchResult(displayResults, fetched, true, false);
    }

    private static void offerAddToCollection(SearchResult result, SearchContext context) {
        System.out.print("Add a result to your collection? Enter result # (or press Enter to skip): ");
        String pick = SCANNER.nextLine().trim();
        if (pick.isEmpty()) {
            return;
        }

        try {
            int index = Integer.parseInt(pick) - 1;
            if (index < 0 || index >= result.cards.size()) {
                System.out.println("Invalid result number.");
                return;
            }

            Card selected = result.cards.get(index);
            List<Card> cardsWithIds = resolveCardsWithIds(result, context);
            Card cardToAdd = findMatchingCard(cardsWithIds, selected);

            if (cardToAdd == null) {
                System.out.println("Could not find that card in the database.");
                return;
            }
            collection.add(cardToAdd.getId());
            System.out.printf("Added to collection: %s [%s] %s - $%.2f%n",
                    cardToAdd.getName(), cardToAdd.getSetName(),
                    cardToAdd.getCondition(), cardToAdd.getPrice());
        } catch (NumberFormatException e) {
            System.out.println("Please enter a valid result number.");
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        } catch (SQLException e) {
            System.out.println("Database error: " + e.getMessage());
        }
    }

    private static Card findMatchingCard(List<Card> cards, Card target) {
        for (Card card : cards) {
            if (card.getName().equalsIgnoreCase(target.getName())
                    && card.getSetName().equalsIgnoreCase(target.getSetName())
                    && card.getLanguage().equalsIgnoreCase(target.getLanguage())
                    && card.getCondition().equalsIgnoreCase(target.getCondition())) {
                return card;
            }
        }
        return null;
    }

    private static List<Card> resolveCardsWithIds(SearchResult result, SearchContext context)
            throws SQLException {
        if (result.persisted) {
            return result.cards;
        }

        repo.importFromPriceCharting(result.imports);
        return repo.search(context.name, context.setFilter, context.languageFilter);
    }

    private static List<PriceChartingClient.CardImport> toImports(List<Card> cards) {
        List<PriceChartingClient.CardImport> imports = new ArrayList<>();
        for (Card card : cards) {
            imports.add(new PriceChartingClient.CardImport(
                    card.getName(),
                    card.getSetName(),
                    card.getLanguage(),
                    card.getCondition(),
                    card.getPrice(),
                    card.getProductId()
            ));
        }
        return imports;
    }

    private static List<Card> cardsFromImports(List<PriceChartingClient.CardImport> imports) {
        List<Card> cards = new ArrayList<>();
        for (PriceChartingClient.CardImport row : imports) {
            cards.add(new Card(
                    0,
                    row.name,
                    row.setName,
                    row.language,
                    row.condition,
                    row.price,
                    row.productId
            ));
        }
        cards.sort((a, b) -> Double.compare(b.getPrice(), a.getPrice()));
        return cards;
    }

    private static void printSearchResults(List<Card> cards) {
        System.out.println();
        System.out.printf("%-4s  %-8s  %-35s  %-30s  %-10s  %s%n",
                "#", "Price", "Name", "Set", "Language", "Condition");
        System.out.println("-".repeat(100));
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            System.out.printf("%-4d  $%-7.2f  %-35s  %-30s  %-10s  %s%n",
                    i + 1, card.getPrice(), card.getName(),
                    card.getSetName(), card.getLanguage(), card.getCondition());
        }
        System.out.println("-".repeat(100));
    }

    private static void printCardTable(List<Card> cards, boolean showId) {
        System.out.println();
        if (showId) {
            System.out.printf("%-4s  %-5s  %-8s  %-35s  %-30s  %-10s  %s%n",
                    "#", "ID", "Price", "Name", "Set", "Language", "Condition");
            System.out.println("-".repeat(110));
            for (int i = 0; i < cards.size(); i++) {
                Card card = cards.get(i);
                System.out.printf("%-4d  %-5d  $%-7.2f  %-35s  %-30s  %-10s  %s%n",
                        i + 1, card.getId(), card.getPrice(), card.getName(),
                        card.getSetName(), card.getLanguage(), card.getCondition());
            }
            System.out.println("-".repeat(110));
        } else {
            printSearchResults(cards);
        }
    }

    private static final class SearchContext {
        final String name;
        final String setFilter;
        final String languageFilter;

        SearchContext(String name, String setFilter, String languageFilter) {
            this.name = name;
            this.setFilter = setFilter;
            this.languageFilter = languageFilter;
        }
    }

    private static final class SearchResult {
        final List<Card> cards;
        final List<PriceChartingClient.CardImport> imports;
        final boolean fromApi;
        final boolean persisted;

        SearchResult(List<Card> cards, List<PriceChartingClient.CardImport> imports,
                     boolean fromApi, boolean persisted) {
            this.cards = cards;
            this.imports = imports;
            this.fromApi = fromApi;
            this.persisted = persisted;
        }
    }
}
