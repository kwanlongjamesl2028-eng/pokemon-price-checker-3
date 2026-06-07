import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fetches Pokemon card data from the PriceCharting.com REST API and
 * resolves real market prices through {@link PriceFetcher}.
 */
public class PriceChartingClient {
    private static final String API_BASE = "https://www.pricecharting.com";
    private static final String DEFAULT_TOKEN = "c0b53bce27c1bdab90b1605249e600dc43dfd1d5";
    static final Pattern PRODUCT_BLOCK = Pattern.compile("\\{[^{}]*\"product-name\"[^{}]*\\}");
    private static final Pattern ID_FIELD = Pattern.compile("\"id\"\\s*:\\s*\"?(\\d+)\"?");
    private static final Pattern NAME_FIELD = Pattern.compile("\"product-name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    static final Pattern CONSOLE_FIELD = Pattern.compile("\"console-name\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern OFFER_VALUE = Pattern.compile("\"value\"\\s*:\\s*(\\d+)");
    private static final Pattern OFFER_PRICE = Pattern.compile("\"price\"\\s*:\\s*(\\d+)");
    private static final String[] CONDITIONS = {
        "Near Mint", "Lightly Played", "Moderately Played", "Heavily Played"
    };

    private final HttpClient http;
    private final String apiToken;
    private final boolean paidToken;
    private final PriceFetcher priceFetcher;

    public PriceChartingClient() {
        this(System.getenv("PRICECHARTING_API_TOKEN"));
    }

    public PriceChartingClient(String apiToken) {
        this.apiToken = (apiToken != null && !apiToken.isBlank()) ? apiToken : DEFAULT_TOKEN;
        this.paidToken = !DEFAULT_TOKEN.equals(this.apiToken);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.priceFetcher = new PriceFetcher(this);
    }

    public boolean hasPaidToken() {
        return paidToken;
    }

    public String fetchProductJson(String productId) throws IOException, InterruptedException {
        return apiGet("/api/product", "id=" + productId);
    }

    String searchProductsJson(String query) throws IOException, InterruptedException {
        return apiGet("/api/products", "q=" + encode(query));
    }

    /**
     * Read the current ungraded market price from PriceCharting collection data.
     * Works with the public demo API token (no paid subscription required).
     */
    public Double fetchCollectionMarketPrice(String productId)
            throws IOException, InterruptedException {
        String json = apiGet("/api/offers", "id=" + productId + "&status=collection");
        if (!json.contains("\"status\":\"success\"") && !json.contains("\"status\": \"success\"")) {
            return null;
        }

        int offersStart = json.indexOf("\"offers\"");
        if (offersStart < 0) {
            return null;
        }
        int arrayStart = json.indexOf('[', offersStart);
        int arrayEnd = json.indexOf(']', arrayStart);
        if (arrayStart < 0 || arrayEnd < 0) {
            return null;
        }
        String offersBody = json.substring(arrayStart, arrayEnd + 1);

        Matcher valueMatcher = OFFER_VALUE.matcher(offersBody);
        while (valueMatcher.find()) {
            int pennies = Integer.parseInt(valueMatcher.group(1));
            if (pennies > 0) {
                return pennies / 100.0;
            }
        }

        Matcher priceMatcher = OFFER_PRICE.matcher(offersBody);
        while (priceMatcher.find()) {
            int pennies = Integer.parseInt(priceMatcher.group(1));
            if (pennies > 0) {
                return pennies / 100.0;
            }
        }
        return null;
    }

    /**
     * Search PriceCharting for cards matching the query and return rows ready for SQL insert.
     */
    public List<CardImport> fetchCards(String searchName, String setFilter, String languageFilter)
            throws IOException, InterruptedException {
        String query = buildSearchQuery(searchName, setFilter, languageFilter);
        String json = apiGet("/api/products", "q=" + encode(query));

        if (!json.contains("\"status\":\"success\"") && !json.contains("\"status\": \"success\"")) {
            throw new IOException("PriceCharting search failed. Check your internet connection.");
        }

        List<ProductSummary> summaries = parseProductList(json);
        List<CardImport> imports = new ArrayList<>();
        String nameTerm = searchName.trim().toLowerCase();
        String setTerm = setFilter == null ? "" : setFilter.trim().toLowerCase();
        String langTerm = languageFilter == null ? "" : languageFilter.trim().toLowerCase();
        int maxProducts = setTerm.isEmpty() ? 10 : 20;
        int fetched = 0;
        int skipped = 0;

        for (ProductSummary summary : summaries) {
            if (!summary.name.toLowerCase().contains(nameTerm)) {
                continue;
            }
            if (!setTerm.isEmpty() && !summary.setName.toLowerCase().contains(setTerm)) {
                continue;
            }
            if (!langTerm.isEmpty()) {
                String cardLang = inferLanguage(summary.setName).toLowerCase();
                if (!cardLang.contains(langTerm) && !summary.setName.toLowerCase().contains(langTerm)) {
                    continue;
                }
            }
            if (fetched >= maxProducts) {
                break;
            }

            if (paidToken) {
                Thread.sleep(1100);
            } else {
                Thread.sleep(250);
            }

            Map<String, Double> conditionPrices = priceFetcher.resolveConditionPrices(
                    summary.id, summary.name, summary.setName);
            if (conditionPrices == null) {
                skipped++;
                continue;
            }

            String language = inferLanguage(summary.setName);
            for (String condition : CONDITIONS) {
                imports.add(new CardImport(
                        summary.name,
                        summary.setName,
                        language,
                        condition,
                        conditionPrices.get(condition),
                        summary.id
                ));
            }
            fetched++;
        }

        return imports;
    }

    static String buildSearchQuery(String searchName, String setFilter, String languageFilter) {
        StringBuilder query = new StringBuilder(searchName.trim());
        if (setFilter != null && !setFilter.isBlank()) {
            query.append(" ").append(setFilter.trim());
        }
        if (languageFilter != null && !languageFilter.isBlank()) {
            query.append(" ").append(languageFilter.trim());
        }
        query.append(" pokemon");
        return query.toString();
    }

    static String inferLanguage(String setName) {
        String lower = setName.toLowerCase();
        if (lower.contains("japanese")) {
            return "Japanese";
        }
        if (lower.contains("chinese")) {
            return "Chinese";
        }
        if (lower.contains("korean")) {
            return "Korean";
        }
        return "English";
    }

    private String apiGet(String path, String queryParams) throws IOException, InterruptedException {
        String url = API_BASE + path + "?t=" + encode(apiToken) + "&" + queryParams;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "PokemonPriceChecker/1.0")
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("PriceCharting HTTP " + response.statusCode());
        }
        return decodeJsonEscapes(response.body());
    }

    private static List<ProductSummary> parseProductList(String json) {
        List<ProductSummary> products = new ArrayList<>();
        int start = json.indexOf("\"products\"");
        if (start < 0) {
            return products;
        }
        int arrayStart = json.indexOf('[', start);
        int arrayEnd = json.indexOf(']', arrayStart);
        if (arrayStart < 0 || arrayEnd < 0) {
            return products;
        }
        String arrayBody = json.substring(arrayStart + 1, arrayEnd);
        Matcher blockMatcher = PRODUCT_BLOCK.matcher(arrayBody);
        while (blockMatcher.find()) {
            String block = blockMatcher.group();
            ProductSummary summary = parseProductBlock(block);
            if (summary != null) {
                products.add(summary);
            }
        }
        return products;
    }

    private static ProductSummary parseProductBlock(String block) {
        Matcher idMatcher = ID_FIELD.matcher(block);
        Matcher nameMatcher = NAME_FIELD.matcher(block);
        Matcher consoleMatcher = CONSOLE_FIELD.matcher(block);
        if (!idMatcher.find() || !nameMatcher.find() || !consoleMatcher.find()) {
            return null;
        }
        return new ProductSummary(
                idMatcher.group(1),
                nameMatcher.group(1),
                consoleMatcher.group(1)
        );
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String decodeJsonEscapes(String json) {
        return json
                .replace("\\u0026", "&")
                .replace("\\u0027", "'")
                .replace("\\/", "/");
    }

    static final class ProductSummary {
        final String id;
        final String name;
        final String setName;

        ProductSummary(String id, String name, String setName) {
            this.id = id;
            this.name = name;
            this.setName = setName;
        }
    }

    static final class CardImport {
        final String name;
        final String setName;
        final String language;
        final String condition;
        final double price;
        final String productId;

        CardImport(String name, String setName, String language,
                   String condition, double price, String productId) {
            this.name = name;
            this.setName = setName;
            this.language = language;
            this.condition = condition;
            this.price = price;
            this.productId = productId;
        }
    }
}
