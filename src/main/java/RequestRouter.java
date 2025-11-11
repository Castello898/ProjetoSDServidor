import org.json.JSONException;
import org.json.JSONObject;

/**
 * Roteia uma requisição JSON para o controlador apropriado.
 */
public class RequestRouter {

    private final UserController userController;
    private final MovieController movieController;

    public RequestRouter() {
        this.userController = new UserController();
        this.movieController = new MovieController();
    }

    public JSONObject handleRequest(String jsonRequestString) {
        try {
            JSONObject request = new JSONObject(jsonRequestString);
            String operacao = request.getString("operacao");
            String token = request.optString("token", null);

            switch (operacao) {
                // --- Operações Públicas ---
                case "LOGIN": // [cite: 274]
                    return userController.login(request);
                case "CRIAR_USUARIO": // [cite: 279]
                    return userController.register(request);

                // --- Operações de Usuário (Comum) ---
                case "LISTAR_PROPRIO_USUARIO": // [cite: 291]
                    return userController.viewProfile(token);
                case "EDITAR_PROPRIO_USUARIO": // [cite: 299]
                    return userController.updatePassword(token, request);
                case "EXCLUIR_PROPRIO_USUARIO": // [cite: 304]
                    return userController.deleteAccount(token);
                case "LOGOUT": // [cite: 274]
                    return userController.logout(token);

                // --- Operações de Usuário (ADM) ---
                case "LISTAR_USUARIOS": // [cite: 290]
                    return userController.listAllUsers(token);
                case "ADMIN_EDITAR_USUARIO": // [cite: 297]
                    return userController.updateOtherUserPassword(token, request);
                case "ADMIN_EXCLUIR_USUARIO": // [cite: 302]
                    return userController.deleteOtherUser(token, request);

                // --- Operações de Filmes (ADM) ---
                case "CRIAR_FILME": // [cite: 275]
                    return movieController.createMovie(token, request);
                case "EDITAR_FILME": // [cite: 293]
                    return movieController.updateMovie(token, request);
                case "EXCLUIR_FILME": // [cite: 300]
                    return movieController.deleteMovie(token, request);

                // --- Operações de Filmes (Todos) ---
                case "LISTAR_FILMES": // [cite: 281]
                    return movieController.listAllMovies(token);

                // TODO: Adicionar casos para CRUD de Reviews [cite: 278, 295, 301]

                default:
                    return createErrorResponse(400, "Erro: Operação não encontrada ou inválida");
            }

        } catch (JSONException e) {
            return createErrorResponse(422, "Erro: Chaves faltantes ou invalidas"); // [cite: 308]
        } catch (Exception e) {
            e.printStackTrace();
            return createErrorResponse(500, "Erro: Falha interna do servidor"); // [cite: 307]
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