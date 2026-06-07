import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates and initializes the SQLite database from SQL schema files.
 * Resolves paths relative to the project folder so the app works regardless
 * of where VS Code or the terminal was launched from.
 */
public class DatabaseManager {
    private static final String DB_FILE = "cards.db";
    private final Path projectRoot;
    private final Connection connection;

    public DatabaseManager() throws SQLException, IOException {
        projectRoot = findProjectRoot();
        Path dbPath = projectRoot.resolve(DB_FILE);
        connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
    }

    public Connection getConnection() {
        return connection;
    }

    /**
     * Run schema.sql and seed_data.sql to set up the database.
     */
    public void initialize() throws SQLException, IOException {
        runSqlFile("sql/schema.sql");
        runSqlFile("sql/seed_data.sql");
    }

    /**
     * Locate the project root by searching for sql/schema.sql, starting from
     * the working directory and walking up parent folders.
     */
    static Path findProjectRoot() throws IOException {
        Path dir = Paths.get("").toAbsolutePath().normalize();
        for (int i = 0; i < 6; i++) {
            if (Files.isRegularFile(dir.resolve("sql/schema.sql"))) {
                return dir;
            }
            Path parent = dir.getParent();
            if (parent == null) {
                break;
            }
            dir = parent;
        }
        throw new IOException(
                "Could not find project folder. Open the 'pokemon-price-checker' "
                        + "folder in VS Code (File > Open Folder) and run from there.");
    }

    private void runSqlFile(String relativePath) throws SQLException, IOException {
        Path path = projectRoot.resolve(relativePath);
        if (!Files.exists(path)) {
            throw new IOException("SQL file not found: " + path);
        }
        String sql = Files.readString(path);
        try (Statement stmt = connection.createStatement()) {
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
        }
    }

    public void close() throws SQLException {
        connection.close();
    }
}
