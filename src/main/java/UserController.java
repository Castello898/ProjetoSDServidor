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

        // Validação (conforme requisitos)
        String validationError = ValidationService.validateCredentials(username, password);
        if (validationError != null) {
            // ATUALIZAÇÃO: Envia a mensagem de erro específica
            return createErrorResponse(422, validationError); // 422 Unprocessable Entity
        }

        try {
            // Verifica se o usuário já existe
            if (db.findUserByUsername(username) != null) {
                // ATUALIZAÇÃO: Envia a mensagem de erro específica
                return createErrorResponse(409, "Nome de usuário já está em uso."); // 409 Conflict
            }

            // Cria o hash da senha
            String passwordHash = PasswordService.hashPassword(password);

            // Salva no DB (role "user" por padrão)
            db.createUser(username, passwordHash, "user");

            // ALTERAÇÃO: Envia o status como String
            return new JSONObject().put("status", "201"); // 201 Created

        } catch (SQLException e) {
            // ATUALIZAÇÃO: Envia a mensagem de erro específica
            return createErrorResponse(500, "Erro interno de banco de dados ao criar usuário.");
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
                // ATUALIZAÇÃO: Envia a mensagem de erro específica
                return createErrorResponse(401, "Usuário ou senha inválidos."); // 401 Unauthorized
            }

            // Gera o token JWT
            String token = jwt.generateToken(user.getId(), user.getUsername(), user.getRole());

            // ALTERAÇÃO: Envia o status como String
            return new JSONObject()
                    .put("status", "200")
                    .put("token", token); // Conforme protocolo "Login"

        } catch (SQLException e) {
            // ATUALIZAÇÃO: Envia a mensagem de erro específica
            return createErrorResponse(500, "Erro interno de banco de dados durante o login.");
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
            // ALTERAÇÃO: Envia o status como String
            return new JSONObject()
                    .put("status", "200")
                    .put("usuario", username); // Conforme protocolo "Listar Usuário"

        } catch (JwtException e) {
            // ATUALIZAÇÃO: Envia a mensagem de erro específica
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

            // Validação (conforme requisitos)
            String validationError = ValidationService.validateCredentials("valido", newPassword); // (username n/a)
            if (validationError != null) {
                // ATUALIZAÇÃO: Envia a mensagem de erro específica
                return createErrorResponse(422, validationError);
            }

            String newPasswordHash = PasswordService.hashPassword(newPassword);
            db.updateUserPassword(userId, newPasswordHash);

            // ALTERAÇÃO: Envia o status como String
            return new JSONObject().put("status", "200");

        } catch (JwtException e) {
            // ATUALIZAÇÃO: Envia a mensagem de erro específica
            return createErrorResponse(401, "Token inválido ou expirado.");
        } catch (SQLException e) {
            // ATUALIZAÇÃO: Envia a mensagem de erro específica
            return createErrorResponse(500, "Erro interno de banco de dados ao atualizar senha.");
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

            // TODO: Antes de apagar o usuário, apagar as REVIEWS dele

            db.deleteUser(userId);

            // ALTERAÇÃO: Envia o status como String
            return new JSONObject().put("status", "200");

        } catch (JwtException e) {
            // ATUALIZAÇÃO: Envia a mensagem de erro específica
            return createErrorResponse(401, "Token inválido ou expirado.");
        } catch (SQLException e) {
            // ATUALIZAÇÃO: Envia a mensagem de erro específica
            return createErrorResponse(500, "Erro interno de banco de dados ao excluir conta.");
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
            // ALTERAÇÃO: Envia o status como String
            return new JSONObject().put("status", "200");
        } catch (JwtException e) {
            // Mesmo se o token for inválido, o logout "funciona"
            // ALTERAÇÃO: Envia o status como String
            return new JSONObject().put("status", "200");
        }
    }

    // ATUALIZAÇÃO: Adiciona uma sobrecarga para createErrorResponse que aceita uma mensagem
    private JSONObject createErrorResponse(int status, String message) {
        JSONObject response = new JSONObject()
                .put("status", String.valueOf(status));
        if (message != null) {
            // O cliente já está programado para ler a chave "mensagem"
            response.put("mensagem", message);
        }
        return response;
    }

    // Mantém o método antigo para erros genéricos (que chama o novo com mensagem nula)
    private JSONObject createErrorResponse(int status) {
        return createErrorResponse(status, null);
    }
}