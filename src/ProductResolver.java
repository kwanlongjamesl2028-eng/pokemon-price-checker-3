import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates PriceCharting product IDs against card names and resolves
 * correct IDs when stored values are wrong.
 */
public final class ProductResolver {
    private static final Pattern PRODUCT_NAME = Pattern.compile(
            "\"product-name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern PRODUCT_ID = Pattern.compile("\"id\"\\s*:\\s*\"?(\\d+)\"?");

    private final PriceChartingClient client;
    private final Map<String, String> resolvedIds = new LinkedHashMap<>();

    public ProductResolver() {
        this(new PriceChartingClient());
    }

    public ProductResolver(PriceChartingClient client) {
        this.client = client;
    }

    /**
     * Return a product ID that matches the card name and set, resolving via
     * PriceCharting search when the stored ID is missing or mismatched.
     */
    public String resolve(String productId, String cardName, String setName)
            throws IOException, InterruptedException {
        String cacheKey = cardName + "|" + setName;
        if (resolvedIds.containsKey(cacheKey)) {
            return resolvedIds.get(cacheKey);
        }

        if (productId != null && !productId.isBlank()) {
            if (PriceFetcher.isCachedForCard(productId, cardName, setName)) {
                resolvedIds.put(cacheKey, productId);
                return productId;
            }
            if (productMatches(productId, cardName)) {
                resolvedIds.put(cacheKey, productId);
                return productId;
            }
        }

        String lookedUp = lookupProductId(cardName, setName);
        String resolved = lookedUp != null ? lookedUp : productId;
        if (resolved != null && !resolved.isBlank()) {
            resolvedIds.put(cacheKey, resolved);
        }
        return resolved;
    }

    boolean productMatches(String productId, String cardName) throws IOException, InterruptedException {
        try {
            String json = client.fetchProductJson(productId);
            if (!json.contains("\"status\":\"success\"") && !json.contains("\"status\": \"success\"")) {
                return false;
            }
            Matcher nameMatcher = PRODUCT_NAME.matcher(json);
            if (!nameMatcher.find()) {
                return false;
            }
            return namesMatch(nameMatcher.group(1), cardName);
        } catch (IOException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("429")) {
                return true;
            }
            throw ex;
        }
    }

    static boolean namesMatch(String apiName, String cardName) {
        String api = apiName.toLowerCase();
        String expected = cardName.toLowerCase();
        if (api.equals(expected)) {
            return true;
        }
        String expectedKey = nameKey(expected);
        String apiKey = nameKey(api);
        return !expectedKey.isEmpty() && (api.contains(expectedKey) || expected.contains(apiKey));
    }

    private String lookupProductId(String cardName, String setName)
            throws IOException, InterruptedException {
        String query = PriceChartingClient.buildSearchQuery(cardName, setName, null);
        String json = client.searchProductsJson(query);
        String nameLower = cardName.trim().toLowerCase();
        String setLower = setName == null ? "" : setName.trim().toLowerCase();

        Matcher blockMatcher = PriceChartingClient.PRODUCT_BLOCK.matcher(json);
        String fallbackId = null;
        while (blockMatcher.find()) {
            String block = blockMatcher.group();
            Matcher idMatcher = PRODUCT_ID.matcher(block);
            Matcher nameMatcher = PRODUCT_NAME.matcher(block);
            Matcher setMatcher = PriceChartingClient.CONSOLE_FIELD.matcher(block);
            if (!idMatcher.find() || !nameMatcher.find() || !setMatcher.find()) {
                continue;
            }
            String id = idMatcher.group(1);
            String name = nameMatcher.group(1).toLowerCase();
            String set = setMatcher.group(1).toLowerCase();
            if (!nameLower.equals(name) && !name.contains(nameKey(nameLower))) {
                continue;
            }
            if (!setLower.isEmpty() && !set.contains(setLower)) {
                continue;
            }
            if (nameLower.equals(name)) {
                return id;
            }
            fallbackId = id;
        }
        return fallbackId;
    }

    private static String nameKey(String name) {
        int hash = name.indexOf('#');
        String key = hash >= 0 ? name.substring(0, hash) : name;
        return key.replaceAll("\\[.*?\\]", "").trim();
    }
}
