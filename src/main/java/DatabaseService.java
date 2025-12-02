import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public class DatabaseService {

    private static final String DB_URL = "jdbc:h2:./vote_flix_db;AUTO_SERVER=TRUE";
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    private static DatabaseService instance;

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
        String sqlCreateTableUsers = "CREATE TABLE IF NOT EXISTS users (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "username VARCHAR(20) UNIQUE NOT NULL," +
                "password_hash VARCHAR(256) NOT NULL," +
                "role VARCHAR(10) NOT NULL" +
                ");";

        String sqlCreateTableFilmes = "CREATE TABLE IF NOT EXISTS filmes (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "titulo VARCHAR(30) NOT NULL," +
                "diretor VARCHAR(30) NOT NULL," +
                "ano VARCHAR(4) NOT NULL," +
                "generos VARCHAR(255) NOT NULL," +
                "sinopse VARCHAR(250)," +
                "nota_media DECIMAL(3, 1) DEFAULT 0.0," +
                "qtd_avaliacoes INT DEFAULT 0," +
                "CONSTRAINT uc_filme UNIQUE(titulo, diretor, ano)" +
                ");";

        String sqlCreateTableReviews = "CREATE TABLE IF NOT EXISTS reviews (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "id_filme INT NOT NULL," +
                "id_usuario INT NOT NULL," +
                "nome_usuario VARCHAR(20) NOT NULL," +
                "nota INT NOT NULL," +
                "titulo VARCHAR(50)," +
                "descricao VARCHAR(250)," +
                "data VARCHAR(10)," +
                "editado BOOLEAN DEFAULT FALSE," + // [NOVO] Requerido pelo protocolo
                "FOREIGN KEY (id_filme) REFERENCES filmes(id) ON DELETE CASCADE," +
                "FOREIGN KEY (id_usuario) REFERENCES users(id) ON DELETE CASCADE," +
                "CONSTRAINT uc_review UNIQUE(id_filme, id_usuario)" +
                ");";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sqlCreateTableUsers);
            stmt.execute(sqlCreateTableFilmes);
            stmt.execute(sqlCreateTableReviews);
        }
        createAdminUser();
    }

    private void createAdminUser() throws SQLException {
        try {
            String adminPassHash = PasswordService.hashPassword("admin");
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

    // --- MÉTODOS DE FILMES ---

    public void createMovie(String titulo, String diretor, String ano, String generos, String sinopse) throws SQLException {
        String sql = "INSERT INTO filmes (titulo, diretor, ano, generos, sinopse) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, titulo);
            pstmt.setString(2, diretor);
            pstmt.setString(3, ano);
            pstmt.setString(4, generos);
            pstmt.setString(5, sinopse);
            pstmt.executeUpdate();
        }
    }

    // Método auxiliar para converter ResultSet de filme em JSON
    private JSONObject resultSetToMovieJson(ResultSet rs) throws SQLException {
        JSONObject filme = new JSONObject();
        filme.put("id", String.valueOf(rs.getInt("id")));
        filme.put("titulo", rs.getString("titulo"));
        filme.put("diretor", rs.getString("diretor"));
        filme.put("ano", rs.getString("ano"));
        filme.put("genero", new JSONArray(rs.getString("generos").split(",")));
        filme.put("sinopse", rs.getString("sinopse"));
        filme.put("nota", String.format("%.1f", rs.getDouble("nota_media")).replace(',', '.'));
        filme.put("qtd_avaliacoes", String.valueOf(rs.getInt("qtd_avaliacoes")));
        return filme;
    }

    public JSONObject findMovieByIdAsJson(int id) throws SQLException {
        String sql = "SELECT * FROM filmes WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return resultSetToMovieJson(rs);
                }
                return null;
            }
        }
    }

    public List<JSONObject> getAllMoviesAsJson() throws SQLException {
        List<JSONObject> filmes = new ArrayList<>();
        String sql = "SELECT * FROM filmes ORDER BY titulo";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                filmes.add(resultSetToMovieJson(rs));
            }
        }
        return filmes;
    }

    // Métodos updateMovie e deleteMovie mantidos do anterior...
    public void updateMovie(int id, String titulo, String diretor, String ano, String generos, String sinopse) throws SQLException {
        String sql = "UPDATE filmes SET titulo = ?, diretor = ?, ano = ?, generos = ?, sinopse = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, titulo); pstmt.setString(2, diretor); pstmt.setString(3, ano); pstmt.setString(4, generos); pstmt.setString(5, sinopse); pstmt.setInt(6, id);
            pstmt.executeUpdate();
        }
    }
    public void deleteMovie(int id) throws SQLException {
        String sql = "DELETE FROM filmes WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id); pstmt.executeUpdate();
        }
    }

    // --- MÉTODOS DE REVIEWS ---

    public void createReview(int idFilme, int idUsuario, String nomeUsuario, int nota, String titulo, String descricao, String data) throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            // Editado começa como FALSE
            String sqlInsert = "INSERT INTO reviews (id_filme, id_usuario, nome_usuario, nota, titulo, descricao, data, editado) VALUES (?, ?, ?, ?, ?, ?, ?, FALSE)";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlInsert)) {
                pstmt.setInt(1, idFilme);
                pstmt.setInt(2, idUsuario);
                pstmt.setString(3, nomeUsuario);
                pstmt.setInt(4, nota);
                pstmt.setString(5, titulo);
                pstmt.setString(6, descricao);
                pstmt.setString(7, data);
                pstmt.executeUpdate();
            }
            recalculateMovieRating(conn, idFilme);
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
    }

    public void updateReview(int idReview, int nota, String titulo, String descricao, String data) throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            int idFilme = -1;
            String sqlFind = "SELECT id_filme FROM reviews WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlFind)) {
                pstmt.setInt(1, idReview);
                try(ResultSet rs = pstmt.executeQuery()) {
                    if(rs.next()) idFilme = rs.getInt("id_filme");
                }
            }
            if (idFilme == -1) throw new SQLException("Review não encontrada.");

            // Atualiza e seta editado = TRUE
            String sqlUpdate = "UPDATE reviews SET nota = ?, titulo = ?, descricao = ?, data = ?, editado = TRUE WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlUpdate)) {
                pstmt.setInt(1, nota);
                pstmt.setString(2, titulo);
                pstmt.setString(3, descricao);
                pstmt.setString(4, data);
                pstmt.setInt(5, idReview);
                pstmt.executeUpdate();
            }
            recalculateMovieRating(conn, idFilme);
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
    }

    public void deleteReview(int idReview) throws SQLException {
        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false);

            int idFilme = -1;
            String sqlFind = "SELECT id_filme FROM reviews WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlFind)) {
                pstmt.setInt(1, idReview);
                try(ResultSet rs = pstmt.executeQuery()) {
                    if(rs.next()) idFilme = rs.getInt("id_filme");
                }
            }
            if (idFilme == -1) throw new SQLException("Review não encontrada.");

            String sqlDelete = "DELETE FROM reviews WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sqlDelete)) {
                pstmt.setInt(1, idReview);
                pstmt.executeUpdate();
            }
            recalculateMovieRating(conn, idFilme);
            conn.commit();
        } catch (SQLException e) {
            if (conn != null) conn.rollback();
            throw e;
        } finally {
            if (conn != null) { conn.setAutoCommit(true); conn.close(); }
        }
    }

    public List<JSONObject> getReviewsByMovieId(int idFilme) throws SQLException {
        String sql = "SELECT * FROM reviews WHERE id_filme = ? ORDER BY id DESC";
        return getReviewsList(sql, idFilme);
    }

    // NOVO: Método para listar reviews de um usuário específico
    public List<JSONObject> getReviewsByUserId(int idUsuario) throws SQLException {
        String sql = "SELECT * FROM reviews WHERE id_usuario = ? ORDER BY id DESC";
        return getReviewsList(sql, idUsuario);
    }

    private List<JSONObject> getReviewsList(String sql, int idParam) throws SQLException {
        List<JSONObject> reviews = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, idParam);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    JSONObject review = new JSONObject();
                    review.put("id", String.valueOf(rs.getInt("id")));
                    review.put("id_filme", String.valueOf(rs.getInt("id_filme")));
                    review.put("nome_usuario", rs.getString("nome_usuario"));
                    review.put("nota", String.valueOf(rs.getInt("nota")));
                    review.put("titulo", rs.getString("titulo"));
                    review.put("descricao", rs.getString("descricao"));
                    review.put("data", rs.getString("data"));
                    // Adicionado 'editado' como string "true"/"false" conforme exemplo do protocolo [cite: 12]
                    review.put("editado", String.valueOf(rs.getBoolean("editado")));
                    reviews.add(review);
                }
            }
        }
        return reviews;
    }

    public Review findReviewById(int reviewId) throws SQLException {
        String sql = "SELECT * FROM reviews WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, reviewId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Review(rs.getInt("id"), rs.getInt("id_filme"), rs.getInt("id_usuario"), rs.getInt("nota"));
                }
            }
        }
        return null;
    }

    // --- MÉTODOS DE USUÁRIO (Replicados para contexto) ---
    public User findUserByUsername(String username) throws SQLException {
        String sql = "SELECT * FROM users WHERE username = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return new User(rs.getInt("id"), rs.getString("username"), rs.getString("password_hash"), rs.getString("role"));
                return null;
            }
        }
    }

    public void createUser(String username, String passwordHash, String role) throws SQLException {
        String sql = "INSERT INTO users (username, password_hash, role) VALUES (?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username); pstmt.setString(2, passwordHash); pstmt.setString(3, role);
            pstmt.executeUpdate();
        }
    }

    public void updateUserPassword(int userId, String newPasswordHash) throws SQLException {
        String sql = "UPDATE users SET password_hash = ? WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newPasswordHash); pstmt.setInt(2, userId); pstmt.executeUpdate();
        }
    }

    public void deleteUser(int userId) throws SQLException {
        String sql = "DELETE FROM users WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId); pstmt.executeUpdate();
        }
    }

    public User findUserById(int userId) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return new User(rs.getInt("id"), rs.getString("username"), rs.getString("password_hash"), rs.getString("role"));
                return null;
            }
        }
    }

    public List<User> getAllUsers() throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = "SELECT id, username, role FROM users ORDER BY id";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(new User(rs.getInt("id"), rs.getString("username"), "", rs.getString("role")));
            }
        }
        return users;
    }

    public Object[][] getAllUsersForTable() throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        String sql = "SELECT id, username, role FROM users ORDER BY id";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                rows.add(new Object[] { rs.getInt("id"), rs.getString("username"), rs.getString("role") });
            }
        }
        return rows.toArray(new Object[0][]);
    }

    public void deleteReviewsByUserId(int userId) throws SQLException {
        // ... implementação existente (usada ao deletar usuário)
        String sql = "DELETE FROM reviews WHERE id_usuario = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.executeUpdate();
        }
        // Nota: Idealmente recalcularia médias de todos os filmes afetados,
        // mas para simplificar e seguir o fluxo de deleteUser, mantemos assim por hora
        // ou implementamos uma lógica mais complexa se necessário.
    }

    private void recalculateMovieRating(Connection conn, int idFilme) throws SQLException {
        String sqlCalc = "SELECT COUNT(*) as qtd, AVG(CAST(nota AS FLOAT)) as media FROM reviews WHERE id_filme = ?";
        int qtd = 0;
        double media = 0.0;
        try (PreparedStatement pstmt = conn.prepareStatement(sqlCalc)) {
            pstmt.setInt(1, idFilme);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    qtd = rs.getInt("qtd");
                    media = rs.getDouble("media");
                }
            }
        }
        if (qtd == 0) media = 0.0;
        String sqlUpdate = "UPDATE filmes SET nota_media = ?, qtd_avaliacoes = ? WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sqlUpdate)) {
            pstmt.setDouble(1, media);
            pstmt.setInt(2, qtd);
            pstmt.setInt(3, idFilme);
            pstmt.executeUpdate();
        }
    }

    public static class Review {
        public int id, idFilme, idUsuario, nota;
        public Review(int id, int f, int u, int n) { this.id=id; this.idFilme=f; this.idUsuario=u; this.nota=n; }
    }
}