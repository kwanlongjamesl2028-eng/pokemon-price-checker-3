import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * All database access for the cards table using parameterized SQL.
 */
public class CardRepository {
    private final Connection conn;

    public CardRepository(Connection conn) {
        this.conn = conn;
    }

    /**
     * Search cards by name (partial, case-insensitive) with optional filters.
     * Results are sorted by price descending.
     */
    public List<Card> search(String name, String setFilter, String languageFilter)
            throws SQLException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Please enter a valid name");
        }

        StringBuilder sql = new StringBuilder(
                "SELECT id, name, set_name, language, condition, price, product_id "
                        + "FROM cards "
                        + "WHERE name LIKE ? COLLATE NOCASE "
        );
        List<Object> params = new ArrayList<>();
        params.add("%" + name.trim() + "%");

        if (setFilter != null && !setFilter.trim().isEmpty()) {
            sql.append("AND set_name LIKE ? COLLATE NOCASE ");
            params.add("%" + setFilter.trim() + "%");
        }
        if (languageFilter != null && !languageFilter.trim().isEmpty()) {
            sql.append("AND language LIKE ? COLLATE NOCASE ");
            params.add("%" + languageFilter.trim() + "%");
        }

        sql.append("ORDER BY price DESC");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return mapResults(ps.executeQuery());
        }
    }

    /**
     * Store cards fetched from PriceCharting using INSERT OR REPLACE SQL.
     */
    public int importFromPriceCharting(List<PriceChartingClient.CardImport> imports)
            throws SQLException {
        String sql = "INSERT INTO cards (name, set_name, language, condition, price, product_id) "
                + "VALUES (?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(name, set_name, language, condition) "
                + "DO UPDATE SET price = excluded.price, product_id = excluded.product_id";

        int count = 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (PriceChartingClient.CardImport row : imports) {
                ps.setString(1, row.name);
                ps.setString(2, row.setName);
                ps.setString(3, row.language);
                ps.setString(4, row.condition);
                ps.setDouble(5, row.price);
                ps.setString(6, row.productId);
                ps.addBatch();
                count++;
            }
            ps.executeBatch();
        }
        return count;
    }

    /**
     * Look up a single card by its primary key.
     */
    public Card findById(int id) throws SQLException {
        String sql = "SELECT id, name, set_name, language, condition, price, product_id "
                + "FROM cards WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            List<Card> results = mapResults(ps.executeQuery());
            return results.isEmpty() ? null : results.get(0);
        }
    }

    /**
     * Return all cards sorted by price descending.
     */
    public List<Card> findAll() throws SQLException {
        String sql = "SELECT id, name, set_name, language, condition, price, product_id "
                + "FROM cards ORDER BY price DESC";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            return mapResults(ps.executeQuery());
        }
    }

    /**
     * Run a raw SELECT query and print formatted results (SQL explorer).
     */
    public void runQueryAndPrint(String query) throws SQLException {
        if (!query.trim().toUpperCase().startsWith("SELECT")) {
            throw new IllegalArgumentException("Only SELECT queries are allowed.");
        }

        try (PreparedStatement ps = conn.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            printResultSet(rs);
        }
    }

    /**
     * SQL aggregation: average price per set for a given card name.
     */
    public void printAveragePriceBySet(String cardName) throws SQLException {
        String sql = "SELECT set_name AS set, "
                + "       ROUND(AVG(price), 2) AS avg_price, "
                + "       COUNT(*) AS condition_count "
                + "FROM cards "
                + "WHERE name LIKE ? COLLATE NOCASE "
                + "GROUP BY set_name "
                + "ORDER BY avg_price DESC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, "%" + cardName.trim() + "%");
            printResultSet(ps.executeQuery());
        }
    }

    private List<Card> mapResults(ResultSet rs) throws SQLException {
        List<Card> cards = new ArrayList<>();
        while (rs.next()) {
            cards.add(new Card(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getString("set_name"),
                    rs.getString("language"),
                    rs.getString("condition"),
                    rs.getDouble("price"),
                    rs.getString("product_id")
            ));
        }
        return cards;
    }

    private void printResultSet(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int cols = meta.getColumnCount();

        int[] widths = new int[cols];
        for (int i = 1; i <= cols; i++) {
            widths[i - 1] = meta.getColumnLabel(i).length();
        }

        List<String[]> rows = new ArrayList<>();
        while (rs.next()) {
            String[] row = new String[cols];
            for (int i = 1; i <= cols; i++) {
                String val = rs.getString(i);
                if (val == null) {
                    val = "";
                }
                row[i - 1] = val;
                widths[i - 1] = Math.max(widths[i - 1], val.length());
            }
            rows.add(row);
        }

        for (int i = 1; i <= cols; i++) {
            System.out.printf("%-" + (widths[i - 1] + 2) + "s", meta.getColumnLabel(i));
        }
        System.out.println();

        for (String[] row : rows) {
            for (int i = 0; i < cols; i++) {
                System.out.printf("%-" + (widths[i] + 2) + "s", row[i]);
            }
            System.out.println();
        }

        if (rows.isEmpty()) {
            System.out.println("(no rows)");
        }
    }
}
