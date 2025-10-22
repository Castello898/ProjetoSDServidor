import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.json.JSONObject;

import java.sql.SQLException;

public class UserController {

    private final DatabaseService db;
    private final JwtService jwt;

    public UserController() {
        this.db = DatabaseService.getInstance();
        this.jwt = new JwtService();
    }

    /**
     * Operação: CRIAR_USUARIO
     * (Protocolo da imagem "Create")
     */
    public JSONObject register(JSONObject request) {
        JSONObject userData = request.getJSONObject("usuario");
        String username = userData.getString("nome");
        String password = userData.getString("senha");

        // Validação (conforme requisitos )
        String validationError = ValidationService.validateCredentials(username, password);
        if (validationError != null) {
            return createErrorResponse(422, validationError); // 422 Unprocessable Entity
        }

        try {
            // Verifica se o usuário já existe [cite: 40]
            if (db.findUserByUsername(username) != null) {
                return createErrorResponse(409, "Conflito: Nome de usuário já existe."); // 409 Conflict
            }

            // Cria o hash da senha
            String passwordHash = PasswordService.hashPassword(password);

            // Salva no DB (role "user" por padrão [cite: 75])
            db.createUser(username, passwordHash, "user");

            return new JSONObject().put("status", 201); // 201 Created

        } catch (SQLException e) {
            return createErrorResponse(500, "Erro de banco de dados ao criar usuário.");
        }
    }

    /**
     * Operação: LOGIN
     * (Protocolo da imagem "Login")
     */
    public JSONObject login(JSONObject request) {
        String username = request.getString("usuario");
        String password = request.getString("senha");

        try {
            User user = db.findUserByUsername(username);

            // Valida usuário e senha
            if (user == null || !PasswordService.checkPassword(password, user.getPasswordHash())) {
                return createErrorResponse(401, "Não autorizado: Credenciais inválidas."); // 401 Unauthorized
            }

            // Gera o token JWT [cite: 33]
            String token = jwt.generateToken(user.getId(), user.getUsername(), user.getRole());

            return new JSONObject()
                    .put("status", 200)
                    .put("token", token); // Conforme protocolo "Login"

        } catch (SQLException e) {
            return createErrorResponse(500, "Erro de banco de dados ao tentar logar.");
        }
    }

    /**
     * Operação: LISTAR_PROPRIO_USUARIO
     * (Protocolo da imagem "Listar Usuário")
     */
    public JSONObject viewProfile(String token) {
        try {
            Claims claims = jwt.validateAndGetClaims(token);
            String username = claims.getSubject();

            // O cliente espera uma string "usuario" (conforme DashboardPanel.java)
            return new JSONObject()
                    .put("status", 200)
                    .put("usuario", username); // Conforme protocolo "Listar Usuário"

        } catch (JwtException e) {
            return createErrorResponse(401, "Token inválido ou expirado.");
        }
    }

    /**
     * Operação: EDITAR_PROPRIO_USUARIO
     * (Protocolo da imagem "Editar Usuário Próprio")
     */
    public JSONObject updatePassword(String token, JSONObject request) {
        try {
            Claims claims = jwt.validateAndGetClaims(token);
            int userId = claims.get("id", Integer.class);

            String newPassword = request.getJSONObject("usuario").getString("senha");

            // Validação (conforme requisitos [cite: 76, 78])
            String validationError = ValidationService.validateCredentials("valido", newPassword); // (username n/a)
            if (validationError != null) {
                return createErrorResponse(422, validationError);
            }

            String newPasswordHash = PasswordService.hashPassword(newPassword);
            db.updateUserPassword(userId, newPasswordHash);

            return new JSONObject().put("status", 200);

        } catch (JwtException e) {
            return createErrorResponse(401, "Token inválido ou expirado.");
        } catch (SQLException e) {
            return createErrorResponse(500, "Erro de banco de dados ao atualizar senha.");
        }
    }

    /**
     * Operação: EXCLUIR_PROPRIO_USUARIO
     * (Protocolo da imagem "Excluir Usuário Próprio")
     */
    public JSONObject deleteAccount(String token) {
        try {
            Claims claims = jwt.validateAndGetClaims(token);
            int userId = claims.get("id", Integer.class);

            // TODO: Antes de apagar o usuário, apagar as REVIEWS dele [cite: 11]

            db.deleteUser(userId);

            return new JSONObject().put("status", 200);

        } catch (JwtException e) {
            return createErrorResponse(401, "Token inválido ou expirado.");
        } catch (SQLException e) {
            return createErrorResponse(500, "Erro de banco de dados ao excluir conta.");
        }
    }

    /**
     * Operação: LOGOUT
     * (Protocolo da imagem "Logout")
     */
    public JSONObject logout(String token) {
        try {
            // Apenas validamos o token. O JWT é "stateless",
            // o cliente que deve destruir o token.
            jwt.validateAndGetClaims(token);
            return new JSONObject().put("status", 200);
        } catch (JwtException e) {
            // Mesmo se o token for inválido, o logout "funciona"
            return new JSONObject().put("status", 200);
        }
    }

    private JSONObject createErrorResponse(int status, String message) {
        return new JSONObject()
                .put("status", status)
                .put("mensagem", message);
    }
}