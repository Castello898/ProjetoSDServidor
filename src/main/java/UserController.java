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
            // ATUALIZAÇÃO: Protocolo define 405 para campos inválidos (regex, tamanho)
            return createErrorResponse(405, "Erro: Campos inválidos, verifique o tipo e quantidade de caracteres");
        }

        try {
            // Verifica se o usuário já existe
            if (db.findUserByUsername(username) != null) {
                // ATUALIZAÇÃO: Mensagem de erro padrão do protocolo
                return createErrorResponse(409, "Erro: Recurso ja existe");
            }

            // Cria o hash da senha
            String passwordHash = PasswordService.hashPassword(password);

            // Salva no DB (role "user" por padrão)
            db.createUser(username, passwordHash, "user");

            // ATUALIZAÇÃO: Adiciona "mensagem" de sucesso conforme protocolo
            return new JSONObject()
                    .put("status", "201")
                    .put("mensagem", "Sucesso: Recurso cadastrado");

        } catch (SQLException e) {
            // ATUALIZAÇÃO: Mensagem de erro padrão do protocolo
            return createErrorResponse(500, "Erro: Falha interna do servidor");
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
                // ATUALIZAÇÃO: Protocolo de Requisições NÃO permite 401 aqui.
                // O erro 403 (sem permissão) é o mais próximo da lista de erros permitidos (400, 403, 422, 500).
                return createErrorResponse(403, "Erro: sem permissão");
            }

            // Gera o token JWT
            String token = jwt.generateToken(user.getId(), user.getUsername(), user.getRole());

            // ATUALIZAÇÃO: Adiciona "mensagem" de sucesso conforme protocolo
            return new JSONObject()
                    .put("status", "200")
                    .put("mensagem", "Sucesso: operação realizada com sucesso")
                    .put("token", token);

        } catch (SQLException e) {
            // ATUALIZAÇÃO: Mensagem de erro padrão do protocolo
            return createErrorResponse(500, "Erro: Falha interna do servidor");
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

            // ATUALIZAÇÃO: Adiciona "mensagem" de sucesso conforme protocolo
            return new JSONObject()
                    .put("status", "200")
                    .put("mensagem", "Sucesso: operação realizada com sucesso")
                    .put("usuario", username); // Conforme protocolo "Listar Usuário"

        } catch (JwtException e) {
            // ATUALIZAÇÃO: Mensagem de erro padrão do protocolo
            return createErrorResponse(401, "Erro: Token inválido");
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
                // ATUALIZAÇÃO: Protocolo define 405 para campos inválidos (regex, tamanho)
                return createErrorResponse(405, "Erro: Campos inválidos, verifique o tipo e quantidade de caracteres");
            }

            String newPasswordHash = PasswordService.hashPassword(newPassword);
            db.updateUserPassword(userId, newPasswordHash);

            // ATUALIZAÇÃO: Adiciona "mensagem" de sucesso conforme protocolo
            return new JSONObject()
                    .put("status", "200")
                    .put("mensagem", "Sucesso: operação realizada com sucesso");

        } catch (JwtException e) {
            // ATUALIZAÇÃO: Mensagem de erro padrão do protocolo
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (SQLException e) {
            // ATUALIZAÇÃO: Mensagem de erro padrão do protocolo
            return createErrorResponse(500, "Erro: Falha interna do servidor");
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

            // ATUALIZAÇÃO: Adiciona "mensagem" de sucesso conforme protocolo
            return new JSONObject()
                    .put("status", "200")
                    .put("mensagem", "Sucesso: operação realizada com sucesso");

        } catch (JwtException e) {
            // ATUALIZAÇÃO: Mensagem de erro padrão do protocolo
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (SQLException e) {
            // ATUALIZAÇÃO: Mensagem de erro padrão do protocolo
            return createErrorResponse(500, "Erro: Falha interna do servidor");
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

            // ATUALIZAÇÃO: Adiciona "mensagem" de sucesso conforme protocolo
            return new JSONObject()
                    .put("status", "200")
                    .put("mensagem", "Sucesso: Operação realizada com sucesso");

        } catch (JwtException e) {
            // ATUALIZAÇÃO: Protocolo EXIGE 401 para token inválido no logout
            return createErrorResponse(401, "Erro: Token inválido");
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