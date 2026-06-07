import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves real market prices from PriceCharting.com.
 *
 * Priority:
 * 1. Paid API loose-price field (PRICECHARTING_API_TOKEN)
 * 2. Cached prices in data/price_cache.json
 * 3. PriceCharting collection offers API (public demo token)
 * 4. Python playwright fetcher script (scripts/fetch_price.py)
 */
public final class PriceFetcher {
    private static final Pattern LOOSE_PRICE = Pattern.compile("\"loose-price\"\\s*:\\s*(\\d+)");
    private static final Path CACHE_PATH = Path.of("data", "price_cache.json");

    private static Map<String, CachedPrice> cache;
    private final PriceChartingClient client;

    public PriceFetcher() {
        this(null); 
    }

    public PriceFetcher(PriceChartingClient client) {
        this.client = client;
        ensureCacheLoaded();
    }

    /**
     * Resolve Near Mint price, or null when no source is available.
     */
    public Double resolveNearMintPrice(String productId, String productName, String setName)
            throws IOException, InterruptedException {
        Double apiPrice = fetchApiLoosePrice(productId);
        if (apiPrice != null) {
            return apiPrice;
        }

        CachedPrice cached = getCached(productId);
        if (cached != null) {
            return cached.nearMint;
        }

        Double livePrice = fetchCollectionMarketPrice(productId, productName, setName);
        if (livePrice != null) {
            return livePrice;
        }

        Double scraped = runPythonFetcher(productId);
        if (scraped != null) {
            reloadCache();
            return scraped;
        }

        return null;
    }

    public double getNearMintPrice(String productId, String productName, String setName)
            throws IOException, InterruptedException {
        Double price = resolveNearMintPrice(productId, productName, setName);
        if (price != null) {
            return price;
        }

        throw new IOException(
                "No price available for " + productName + " [" + setName + "]. "
                        + "Set PRICECHARTING_API_TOKEN or run: python3 scripts/fetch_price.py "
                        + productId);
    }

    /**
     * Resolve all condition prices, or null when no source is available.
     */
    public Map<String, Double> resolveConditionPrices(String productId, String productName, String setName)
            throws IOException, InterruptedException {
        CachedPrice cached = getCached(productId);
        if (cached != null && cached.conditions != null && !cached.conditions.isEmpty()) {
            return cached.conditions;
        }

        Double nearMint = resolveNearMintPrice(productId, productName, setName);
        if (nearMint == null) {
            return null;
        }
        return buildConditionPrices(nearMint);
    }

    public Map<String, Double> getConditionPrices(String productId, String productName, String setName)
            throws IOException, InterruptedException {
        Map<String, Double> prices = resolveConditionPrices(productId, productName, setName);
        if (prices != null) {
            return prices;
        }
        throw new IOException(
                "No price available for " + productName + " [" + setName + "]. "
                        + "Set PRICECHARTING_API_TOKEN or run: python3 scripts/fetch_price.py "
                        + productId);
    }

    static Map<String, Double> buildConditionPrices(double nearMint) {
        Map<String, Double> prices = new LinkedHashMap<>();
        prices.put("Near Mint", round(nearMint));
        prices.put("Lightly Played", round(nearMint * 0.78));
        prices.put("Moderately Played", round(nearMint * 0.58));
        prices.put("Heavily Played", round(nearMint * 0.38));
        return prices;
    }

    private Double fetchCollectionMarketPrice(String productId, String productName, String setName)
            throws IOException, InterruptedException {
        if (client == null) {
            return null;
        }
        Double price = client.fetchCollectionMarketPrice(productId);
        if (price == null) {
            return null;
        }
        persistToCache(productId, productName, setName, price);
        return price;
    }

    private void persistToCache(String productId, String productName, String setName, double nearMint)
            throws IOException {
        ensureCacheLoaded();
        Map<String, Double> conditions = buildConditionPrices(nearMint);
        cache.put(productId, new CachedPrice(nearMint, conditions, productName, setName));
        writeCacheEntry(productId, productName, setName, nearMint, conditions);
    }

    private void writeCacheEntry(String productId, String productName, String setName,
                                 double nearMint, Map<String, Double> conditions)
            throws IOException {
        Path path = resolveCachePath();
        String json;
        if (Files.exists(path)) {
            json = Files.readString(path);
        } else {
            Files.createDirectories(path.getParent());
            json = "{\n}\n";
        }

        String entry = formatCacheEntry(productId, productName, setName, nearMint, conditions);
        Pattern existing = Pattern.compile(
                "\"" + Pattern.quote(productId) + "\"\\s*:\\s*\\{[\\s\\S]*?\\}(?=\\s*,|\\s*\\})");
        Matcher matcher = existing.matcher(json);
        if (matcher.find()) {
            json = matcher.replaceFirst(entry);
        } else if (json.trim().equals("{}")) {
            json = "{\n" + entry + "\n}\n";
        } else {
            int close = json.lastIndexOf('}');
            String prefix = json.substring(0, close).trim();
            if (prefix.endsWith("{")) {
                json = prefix + "\n" + entry + "\n}\n";
            } else {
                json = prefix + ",\n" + entry + "\n}\n";
            }
        }
        Files.writeString(path, json);
    }

    private static String formatCacheEntry(String productId, String productName, String setName,
                                           double nearMint, Map<String, Double> conditions) {
        StringBuilder sb = new StringBuilder();
        sb.append("  \"").append(productId).append("\": {\n");
        sb.append("    \"near_mint\": ").append(String.format("%.2f", nearMint)).append(",\n");
        sb.append("    \"conditions\": {\n");
        int i = 0;
        for (Map.Entry<String, Double> row : conditions.entrySet()) {
            sb.append("      \"").append(row.getKey()).append("\": ")
                    .append(String.format("%.2f", row.getValue()));
            if (++i < conditions.size()) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("    },\n");
        sb.append("    \"name\": \"").append(escapeJson(productName)).append("\",\n");
        sb.append("    \"set_name\": \"").append(escapeJson(setName)).append("\",\n");
        sb.append("    \"source\": \"pricecharting.com collection value\"\n");
        sb.append("  }");
        return sb.toString();
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Double fetchApiLoosePrice(String productId) throws IOException, InterruptedException {
        if (client == null || !client.hasPaidToken()) {
            return null;
        }
        String json = client.fetchProductJson(productId);
        Matcher matcher = LOOSE_PRICE.matcher(json);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1)) / 100.0;
        }
        return null;
    }

    private CachedPrice getCached(String productId) {
        ensureCacheLoaded();
        return cache.get(productId);
    }

    /**
     * True when the local price cache already maps this product to the card.
     */
    static boolean isCachedForCard(String productId, String cardName, String setName) {
        ensureCacheLoaded();
        CachedPrice cached = cache.get(productId);
        if (cached == null) {
            return false;
        }
        if (cached.cardName == null || cached.setName == null) {
            return true;
        }
        return namesMatch(cached.cardName, cardName)
                && cached.setName.equalsIgnoreCase(setName);
    }

    private static boolean namesMatch(String cachedName, String cardName) {
        String cached = cachedName.toLowerCase();
        String expected = cardName.toLowerCase();
        if (cached.equals(expected)) {
            return true;
        }
        int hash = expected.indexOf('#');
        String key = hash >= 0 ? expected.substring(0, hash) : expected;
        key = key.replaceAll("\\[.*?\\]", "").trim();
        return !key.isEmpty() && cached.contains(key);
    }

    private static void ensureCacheLoaded() {
        if (cache != null) {
            return;
        }
        cache = new LinkedHashMap<>();
        try {
            Path path = resolveCachePath();
            if (!Files.exists(path)) {
                return;
            }
            String json = Files.readString(path);
            parseCacheJson(json);
        } catch (IOException ignored) {
            cache = new LinkedHashMap<>();
        }
    }

    private static Path resolveCachePath() throws IOException {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        for (int i = 0; i < 6; i++) {
            Path candidate = cwd.resolve(CACHE_PATH);
            if (Files.exists(candidate)) {
                return candidate;
            }
            Path parent = cwd.getParent();
            if (parent == null) {
                break;
            }
            cwd = parent;
        }
        return CACHE_PATH;
    }

    private static void parseCacheJson(String json) {
        Pattern entry = Pattern.compile(
                "\"(\\d+)\"\\s*:\\s*\\{([\\s\\S]*?)\\}(?=\\s*,\\s*\"|\\s*\\})",
                Pattern.MULTILINE);
        Matcher matcher = entry.matcher(json);
        while (matcher.find()) {
            String id = matcher.group(1);
            String body = matcher.group(2);
            Matcher nearMintMatcher = Pattern.compile("\"near_mint\"\\s*:\\s*([0-9.]+)").matcher(body);
            if (!nearMintMatcher.find()) {
                continue;
            }
            double nearMint = Double.parseDouble(nearMintMatcher.group(1));
            Map<String, Double> conditions = parseConditionBlock(body);
            if (conditions.isEmpty()) {
                conditions = buildConditionPrices(nearMint);
            }
            String cardName = extractJsonString(body, "name");
            String setName = extractJsonString(body, "set_name");
            cache.put(id, new CachedPrice(nearMint, conditions, cardName, setName));
        }
    }

    private static String extractJsonString(String body, String field) {
        Matcher matcher = Pattern.compile(
                "\"" + field + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
        ).matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static Map<String, Double> parseConditionBlock(String body) {
        Map<String, Double> conditions = new LinkedHashMap<>();
        Matcher block = Pattern.compile("\"conditions\"\\s*:\\s*\\{([\\s\\S]*?)\\}").matcher(body);
        if (!block.find()) {
            return conditions;
        }
        Matcher priceMatcher = Pattern.compile(
                "\"(Near Mint|Lightly Played|Moderately Played|Heavily Played)\"\\s*:\\s*([0-9.]+)"
        ).matcher(block.group(1));
        while (priceMatcher.find()) {
            conditions.put(priceMatcher.group(1), Double.parseDouble(priceMatcher.group(2)));
        }
        return conditions;
    }

    private void reloadCache() {
        cache = null;
        ensureCacheLoaded();
    }

    private Double runPythonFetcher(String productId) {
        try {
            Path projectRoot = DatabaseManager.findProjectRoot();
            Path script = projectRoot.resolve("scripts/fetch_price.py");
            if (!Files.exists(script)) {
                return null;
            }

            ProcessBuilder builder = new ProcessBuilder(
                    "python3", script.toString(), productId);
            builder.directory(projectRoot.toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exit = process.waitFor();
            if (exit != 0) {
                return null;
            }

            Matcher nearMint = Pattern.compile("\"near_mint\"\\s*:\\s*([0-9.]+)").matcher(output);
            if (nearMint.find()) {
                return Double.parseDouble(nearMint.group(1));
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    static final class CachedPrice {
        final double nearMint;
        final Map<String, Double> conditions;
        final String cardName;
        final String setName;

        CachedPrice(double nearMint, Map<String, Double> conditions) {
            this(nearMint, conditions, null, null);
        }

        CachedPrice(double nearMint, Map<String, Double> conditions,
                    String cardName, String setName) {
            this.nearMint = nearMint;
            this.conditions = conditions;
            this.cardName = cardName;
            this.setName = setName;
        }
    }
}
