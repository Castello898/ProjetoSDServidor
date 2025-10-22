import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseService {

    private static final String DB_URL = "jdbc:h2:./vote_flix_db"; // Salva o DB no arquivo
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    private static DatabaseService instance;

    // Singleton pattern
    public static synchronized DatabaseService getInstance() {
        if (instance == null) {
            instance = new DatabaseService();
        }
        return instance;
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    public void initializeDatabase() throws SQLException {
        String sqlCreateTable = "CREATE TABLE IF NOT EXISTS users (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "username VARCHAR(20) UNIQUE NOT NULL," + // [cite: 77]
                "password_hash VARCHAR(256) NOT NULL," +
                "role VARCHAR(10) NOT NULL" +
                ");";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sqlCreateTable);
        }

        // Garante que o usuário admin exista (conforme requisitos )
        try {
            String adminPassHash = PasswordService.hashPassword("admin");
            // "MERGE" é um "INSERT se não existir"
            String sqlCreateAdmin = "MERGE INTO users (username, password_hash, role) " +
                    "KEY(username) VALUES ('admin', ?, 'admin')";
            try (Connection conn = getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sqlCreateAdmin)) {
                pstmt.setString(1, adminPassHash);
                pstmt.executeUpdate();
            }
        } catch (Exception e) {
            throw new SQLException("Falha ao criar usuário admin", e);
        }
    }

    // --- Métodos do CRUD de Usuário ---

    public void createUser(String username, String passwordHash, String role) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, passwordHash);
            pstmt.setString(3, role);
            pstmt.executeUpdate();
        }
    }

    public User findUserByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new User(
                            rs.getInt("id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getString("role")
                    );
                }
                return null; // Não encontrado
            }
        }
    }

    public void updateUserPassword(int userId, String newPasswordHash) throws SQLException {
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newPasswordHash);
            pstmt.setInt(2, userId);
            pstmt.executeUpdate();
        }
    }

    public void deleteUser(int userId) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.executeUpdate();
        }
    }
}