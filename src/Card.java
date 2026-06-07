/**
 * Represents a single row in the cards table.
 */
public class Card {
    private final int id;
    private final String name;
    private final String setName;
    private final String language;
    private final String condition;
    private final double price;
    private final String productId;

    public Card(int id, String name, String setName, String language,
                String condition, double price, String productId) {
        this.id = id;
        this.name = name;
        this.setName = setName;
        this.language = language;
        this.condition = condition;
        this.price = price;
        this.productId = productId;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getSetName() { return setName; }
    public String getLanguage() { return language; }
    public String getCondition() { return condition; }
    public double getPrice() { return price; }
    public String getProductId() { return productId; }

    @Override
    public String toString() {
        return String.format("$%.2f  %-35s  %-30s  %-8s  %s",
                price, name, setName, language, condition);
    }
}
