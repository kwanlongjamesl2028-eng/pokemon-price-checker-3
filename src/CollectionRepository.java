import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SQL access for the user's saved card collection.
 * Data persists in the SQLite database across sessions.
 */
public class CollectionRepository {
    private final Connection conn;

    public CollectionRepository(Connection conn) {
        this.conn = conn;
    }

    /**
     * Return all cards in the user's collection, joined with current price data.
     */
    public List<Card> findAll() throws SQLException {
        String sql = "SELECT c.id, c.name, c.set_name, c.language, c.condition, c.price, c.product_id "
                + "FROM collection col "
                + "INNER JOIN cards c ON c.id = col.card_id "
                + "ORDER BY col.added_at DESC";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            return mapResults(ps.executeQuery());
        }
    }

    /**
     * Add a card to the collection by its cards-table ID.
     */
    public void add(int cardId) throws SQLException {
        if (!cardExists(cardId)) {
            throw new IllegalArgumentException("No card found with ID " + cardId
                    + ". Search for a card first (option 1) to find its ID.");
        }
        if (isInCollection(cardId)) {
            throw new IllegalArgumentException("That card is already in your collection.");
        }

        String sql = "INSERT INTO collection (card_id) VALUES (?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cardId);
            ps.executeUpdate();
        }
    }

    /**
     * Remove a card from the collection by its cards-table ID.
     */
    public int remove(int cardId) throws SQLException {
        String sql = "DELETE FROM collection WHERE card_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cardId);
            return ps.executeUpdate();
        }
    }

    public int count() throws SQLException {
        String sql = "SELECT COUNT(*) FROM collection";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private boolean cardExists(int cardId) throws SQLException {
        String sql = "SELECT 1 FROM cards WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cardId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean isInCollection(int cardId) throws SQLException {
        String sql = "SELECT 1 FROM collection WHERE card_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, cardId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
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
}
