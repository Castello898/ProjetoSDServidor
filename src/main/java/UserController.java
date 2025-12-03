import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.List;
import java.sql.SQLException;

public class UserController {

    private final DatabaseService db;
    private final JwtService jwt;

    public UserController() {
        this.db = DatabaseService.getInstance();
        this.jwt = new JwtService();
    }

    private Claims validateAdmin(String token) throws JwtException, SecurityException {
        Claims claims = jwt.validateAndGetClaims(token);
        if (!"admin".equals(claims.get("role", String.class))) {
            throw new SecurityException("Acesso negado: Requer privilégios de ADM");
        }
        return claims;
    }

    /**
     * Operação: CRIAR_USUARIO
     */
    public JSONObject register(JSONObject request) {
        JSONObject userData = request.getJSONObject("usuario");
        String username = userData.getString("nome");
        String password = userData.getString("senha");

        String validationError = ValidationService.validateCredentials(username, password);
        if (validationError != null) {
            return createErrorResponse(405, "Erro: Campos inválidos, verifique o tipo e quantidade de caracteres");
        }

        if ("admin".equalsIgnoreCase(username)) {
            return createErrorResponse(403, "Erro: O nome de usuário 'admin' é reservado.");
        }

        try {
            if (db.findUserByUsername(username) != null) {
                return createErrorResponse(409, "Erro: Recurso ja existe");
            }

            String passwordHash = PasswordService.hashPassword(password);
            db.createUser(username, passwordHash, "user");

            return new JSONObject()
                    .put("status", "201")
                    .put("mensagem", "Sucesso: Recurso cadastrado");

        } catch (SQLException e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    /**
     * Operação: LOGIN
     */
    public JSONObject login(JSONObject request) {
        String username = request.getString("usuario");
        String password = request.getString("senha");

        try {
            User user = db.findUserByUsername(username);

            if (user == null || !PasswordService.checkPassword(password, user.getPasswordHash())) {
                return createErrorResponse(403, "Erro: sem permissão");
            }

            String token = jwt.generateToken(user.getId(), user.getUsername(), user.getRole());

            return new JSONObject()
                    .put("status", "200")
                    .put("mensagem", "Sucesso: operação realizada com sucesso")
                    .put("token", token);

        } catch (SQLException e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    /**
     * Operação: LISTAR_PROPRIO_USUARIO
     */
    public JSONObject viewProfile(String token) {
        try {
            Claims claims = jwt.validateAndGetClaims(token);
            String username = claims.getSubject();

            return new JSONObject()
                    .put("status", "200")
                    .put("mensagem", "Sucesso: operação realizada com sucesso")
                    .put("usuario", username);

        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        }
    }

    /**
     * Operação: EDITAR_PROPRIO_USUARIO
     */
    public JSONObject updatePassword(String token, JSONObject request) {
        try {
            Claims claims = jwt.validateAndGetClaims(token);

            // Restrição: Admin não pode alterar a própria senha por aqui
            String role = claims.get("role", String.class);
            if ("admin".equals(role)) {
                return createErrorResponse(403, "Erro: O usuário 'admin' não pode alterar a própria senha.");
            }

            int userId = claims.get("id", Integer.class);

            String newPassword = request.getJSONObject("usuario").getString("senha");

            String validationError = ValidationService.validateCredentials("valido", newPassword);
            if (validationError != null) {
                return createErrorResponse(405, "Erro: Campos inválidos, verifique o tipo e quantidade de caracteres");
            }

            String newPasswordHash = PasswordService.hashPassword(newPassword);
            db.updateUserPassword(userId, newPasswordHash);

            return new JSONObject()
                    .put("status", "200")
                    .put("mensagem", "Sucesso: operação realizada com sucesso");

        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (SQLException e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    /**
     * Operação: EXCLUIR_PROPRIO_USUARIO
     */
    public JSONObject deleteAccount(String token) {
        try {
            Claims claims = jwt.validateAndGetClaims(token);

            String role = claims.get("role", String.class);
            if ("admin".equals(role)) {
                return createErrorResponse(403, "Erro: sem permissão (O usuário 'admin' não pode ser excluído)");
            }

            int userId = claims.get("id", Integer.class);

            // Chama o método corrigido do DBService para apagar reviews E recalcular média
            db.deleteReviewsByUserId(userId);
            db.deleteUser(userId);

            return new JSONObject()
                    .put("status", "200")
                    .put("mensagem", "Sucesso: operação realizada com sucesso");
        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (SQLException e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    /**
     * Operação: LISTAR_USUARIOS
     */
    public JSONObject listAllUsers(String token) {
        try {
            validateAdmin(token);
            List<User> users = db.getAllUsers();
            JSONArray userList = new JSONArray();

            for (User user : users) {
                userList.put(new JSONObject()
                        .put("id", String.valueOf(user.getId()))
                        .put("nome", user.getUsername())
                );
            }
            return new JSONObject()
                    .put("status", "200")
                    .put("mensagem", "Sucesso: operação realizada com sucesso")
                    .put("usuarios", userList);

        } catch (SecurityException e) {
            return createErrorResponse(403, "Erro: sem permissão");
        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (SQLException e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    /**
     * Operação: ADMIN_EDITAR_USUARIO
     */
    public JSONObject updateOtherUserPassword(String token, JSONObject request) {
        try {
            validateAdmin(token);

            int userIdToUpdate = Integer.parseInt(request.getString("id"));
            String newPassword = request.getJSONObject("usuario").getString("senha");

            String newPasswordHash = PasswordService.hashPassword(newPassword);
            db.updateUserPassword(userIdToUpdate, newPasswordHash);

            return createErrorResponse(200, "Sucesso: operação realizada com sucesso");
        } catch (SecurityException e) {
            return createErrorResponse(403, "Erro: sem permissão");
        } catch (NumberFormatException e) {
            return createErrorResponse(400, "Erro: ID inválido");
        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (SQLException e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    /**
     * Operação: ADMIN_EXCLUIR_USUARIO
     */
    public JSONObject deleteOtherUser(String token, JSONObject request) {
        try {
            validateAdmin(token);

            int userIdToDelete = Integer.parseInt(request.getString("id"));

            User user = db.findUserById(userIdToDelete);
            if (user != null && "admin".equals(user.getRole())) {
                return createErrorResponse(403, "Erro: O usuário 'admin' não pode ser excluído.");
            }

            // Chama o método corrigido do DBService para apagar reviews E recalcular média
            db.deleteReviewsByUserId(userIdToDelete);
            db.deleteUser(userIdToDelete);

            return createErrorResponse(200, "Sucesso: operação realizada com sucesso");
        } catch (SecurityException e) {
            return createErrorResponse(403, "Erro: sem permissão");
        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (SQLException e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    /**
     * Operação: LOGOUT
     */
    public JSONObject logout(String token) {
        try {
            jwt.validateAndGetClaims(token);
            return new JSONObject()
                    .put("status", "200")
                    .put("mensagem", "Sucesso: Operação realizada com sucesso");

        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        }
    }

    private JSONObject createErrorResponse(int status, String message) {
        JSONObject response = new JSONObject().put("status", String.valueOf(status));
        if (message != null) {
            response.put("mensagem", message);
        }
        return response;
    }
}