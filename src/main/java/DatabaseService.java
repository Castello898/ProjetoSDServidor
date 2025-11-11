import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList; // Importado
import java.util.List;      // Importado
import org.json.JSONArray;
import org.json.JSONObject;

public class DatabaseService {

    private static final String DB_URL = "jdbc:h2:./vote_flix_db"; // Salva o DB no arquivo
    private static final String DB_USER = "sa";
    private static final String DB_PASSWORD = "";

    // --- CORREÇÃO: O CÓDIGO DO SINGLETON ESTAVA FALTANDO ---
    private static DatabaseService instance;

    // Singleton pattern
    public static synchronized DatabaseService getInstance() {
        if (instance == null) {
            instance = new DatabaseService();
        }
        return instance;
    }
    // --- FIM DA CORREÇÃO ---

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

        // SQL para 'filmes' (usa tipos numéricos para cálculos)
        String sqlCreateTableFilmes = "CREATE TABLE IF NOT EXISTS filmes (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "titulo VARCHAR(30) NOT NULL," +
                "diretor VARCHAR(30) NOT NULL," +
                "ano VARCHAR(4) NOT NULL," + // Ano é string nos requisitos [cite: 544]
                "generos VARCHAR(255) NOT NULL," +
                "sinopse VARCHAR(250)," +
                "nota_media DECIMAL(3, 1) DEFAULT 0.0," + // NUMÉRICO para cálculo da média
                "qtd_avaliacoes INT DEFAULT 0," + // NUMÉRICO para cálculo da média [cite: 534]
                "CONSTRAINT uc_filme UNIQUE(titulo, diretor, ano)" +
                ");";

        // SQL para 'reviews'
        String sqlCreateTableReviews = "CREATE TABLE IF NOT EXISTS reviews (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "id_filme INT NOT NULL," +
                "id_usuario INT NOT NULL," +
                "nome_usuario VARCHAR(20) NOT NULL," +
                "nota INT NOT NULL," + // NUMÉRICO para cálculo da média [cite: 535]
                "titulo VARCHAR(250)," + // O Protocolo (Schemas) [cite: 306] mostra um título
                "descricao VARCHAR(250)," +
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

        // Garante que o usuário admin exista (conforme requisitos)
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

    public List<User> getAllUsers() throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = "SELECT id, username, role FROM users ORDER BY id";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(new User(
                        rs.getInt("id"),
                        rs.getString("username"),
                        "", // Não expor o hash
                        rs.getString("role")
                ));
            }
        }
        return users;
    }

    /**
     * Deleta todas as reviews de um usuário específico.
     * [cite_start](Necessário antes de deletar o usuário [cite: 186])
     */
    public void deleteReviewsByUserId(int userId) throws SQLException {
        String sql = "DELETE FROM reviews WHERE id_usuario = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.executeUpdate();
        }
    }

    /**
     * NOVO MÉTODO: Busca todos os usuários para exibição na JTable da GUI.
     * Não inclui o hash da senha por segurança.
     */
    public Object[][] getAllUsersForTable() throws SQLException {
        List<Object[]> rows = new ArrayList<>();
        // Não seleciona o password_hash
        String sql = "SELECT id, username, role FROM users ORDER BY id";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                rows.add(new Object[] {
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getString("role")
                });
            }
        }
        // Converte a Lista para um array 2D de Objetos
        return rows.toArray(new Object[0][]);
    }

    public User findUserById(int userId) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
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

    public List<JSONObject> getAllMovies() throws SQLException {
        List<JSONObject> filmes = new ArrayList<>();
        String sql = "SELECT id, titulo, diretor, ano, generos, sinopse, nota_media, qtd_avaliacoes FROM filmes ORDER BY titulo";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                JSONObject filme = new JSONObject();
                // Converte os dados do DB para o formato JSON esperado (todas strings)
                filme.put("id", String.valueOf(rs.getInt("id")));
                filme.put("titulo", rs.getString("titulo"));
                filme.put("diretor", rs.getString("diretor"));
                filme.put("ano", rs.getString("ano"));
                // Converte a string "A,B,C" para um array JSON ["A", "B", "C"]
                filme.put("genero", new org.json.JSONArray(rs.getString("generos").split(",")));
                filme.put("sinopse", rs.getString("sinopse"));
                filme.put("nota", String.format("%.1f", rs.getDouble("nota_media")));
                filme.put("qtd_avaliacoes", String.valueOf(rs.getInt("qtd_avaliacoes")));
                filmes.add(filme);
            }
        }
        return filmes;
    }

    public void updateMovie(int id, String titulo, String diretor, String ano, String generos, String sinopse) throws SQLException {
        String sql = "UPDATE filmes SET titulo = ?, diretor = ?, ano = ?, generos = ?, sinopse = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, titulo);
            pstmt.setString(2, diretor);
            pstmt.setString(3, ano);
            pstmt.setString(4, generos);
            pstmt.setString(5, sinopse);
            pstmt.setInt(6, id);
            pstmt.executeUpdate();
        }
    }

    // Método de deleteMovie (recebe int)
    public void deleteMovie(int id) throws SQLException {
        String sql = "DELETE FROM filmes WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        }
    }

    public List<JSONObject> getAllMoviesAsJson() throws SQLException {
        List<JSONObject> filmes = new ArrayList<>();
        String sql = "SELECT * FROM filmes ORDER BY titulo";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                JSONObject filme = new JSONObject();
                // CONVERSÃO PARA STRING (Protocolo)
                filme.put("id", String.valueOf(rs.getInt("id")));
                filme.put("titulo", rs.getString("titulo"));
                filme.put("diretor", rs.getString("diretor"));
                filme.put("ano", rs.getString("ano"));
                filme.put("genero", new JSONArray(rs.getString("generos").split(",")));
                filme.put("sinopse", rs.getString("sinopse"));
                // CONVERSÃO NUMÉRICO -> STRING FORMATADA
                filme.put("nota", String.format("%.1f", rs.getDouble("nota_media")));
                filme.put("qtd_avaliacoes", String.valueOf(rs.getInt("qtd_avaliacoes")));

                filmes.add(filme);
            }
        }
        return filmes;
    }



}