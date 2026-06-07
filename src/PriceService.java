import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Applies current PriceCharting market prices to card records.
 */
public final class PriceService {
    private final PriceFetcher fetcher;
    private final ProductResolver resolver;

    public PriceService() {
        this(new PriceFetcher(), new ProductResolver());
    }

    public PriceService(PriceFetcher fetcher) {
        this(fetcher, new ProductResolver());
    }

    public PriceService(PriceFetcher fetcher, ProductResolver resolver) {
        this.fetcher = fetcher;
        this.resolver = resolver;
    }

    /**
     * Replace stored prices with the latest values from PriceCharting.
     */
    public List<Card> withCurrentPrices(List<Card> cards) throws IOException, InterruptedException {
        List<Card> updated = new ArrayList<>();
        for (Card card : cards) {
            if (card.getProductId() == null || card.getProductId().isBlank()) {
                updated.add(card);
                continue;
            }
            String productId;
            try {
                productId = resolver.resolve(
                        card.getProductId(), card.getName(), card.getSetName());
            } catch (IOException | InterruptedException ex) {
                productId = card.getProductId();
            }
            if (productId == null || productId.isBlank()) {
                updated.add(card);
                continue;
            }
            Map<String, Double> prices = fetcher.resolveConditionPrices(
                    productId, card.getName(), card.getSetName());
            if (prices == null) {
                updated.add(card);
                continue;
            }
            Double price = prices.get(card.getCondition());
            if (price == null) {
                updated.add(card);
                continue;
            }
            updated.add(new Card(
                    card.getId(),
                    card.getName(),
                    card.getSetName(),
                    card.getLanguage(),
                    card.getCondition(),
                    price,
                    card.getProductId()
            ));
        }
        updated.sort((a, b) -> Double.compare(b.getPrice(), a.getPrice()));
        return updated;
    }
}
