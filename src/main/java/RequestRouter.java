import org.json.JSONException;
import org.json.JSONObject;

/**
 * Roteia uma requisição JSON para o controlador apropriado.
 * Segue o protocolo definido na imagem (image_17cd1f.png) e no NetworkService.java.
 */
public class RequestRouter {

    private final UserController userController;

    public RequestRouter() {
        this.userController = new UserController();
    }

    public JSONObject handleRequest(String jsonRequestString) {
        try {
            JSONObject request = new JSONObject(jsonRequestString);
            String operacao = request.getString("operacao"); // [cite: 87]

            // O token é necessário para todas as operações, exceto LOGIN e CRIAR_USUARIO
            String token = request.optString("token", null);

            switch (operacao) {
                // --- Operações Públicas ---
                case "LOGIN":
                    return userController.login(request);
                case "CRIAR_USUARIO":
                    return userController.register(request);

                // --- Operações Autenticadas ---
                case "LISTAR_PROPRIO_USUARIO":
                    return userController.viewProfile(token);
                case "EDITAR_PROPRIO_USUARIO":
                    return userController.updatePassword(token, request);
                case "EXCLUIR_PROPRIO_USUARIO":
                    return userController.deleteAccount(token);
                case "LOGOUT":
                    return userController.logout(token);

                // TODO: Adicionar casos para CRUD de Filmes e Reviews (ADMIN e USER)

                default:
                    return createErrorResponse(400, "Operação desconhecida: " + operacao);
            }

        } catch (JSONException e) {
            return createErrorResponse(400, "Requisição JSON mal formatada: " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return createErrorResponse(500, "Erro interno inesperado no servidor: " + e.getMessage());
        }
    }

    private JSONObject createErrorResponse(int status, String message) {
        return new JSONObject()
                .put("status", status)
                .put("mensagem", message);
    }
}