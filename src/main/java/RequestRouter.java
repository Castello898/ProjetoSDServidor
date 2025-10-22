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
            String operacao = request.getString("operacao"); //

            // O token é necessário para todas as operations, exceto LOGIN e CRIAR_USUARIO
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
                    return createErrorResponse(400);
            }

        } catch (JSONException e) {
            return createErrorResponse(400);
        } catch (Exception e) {
            e.printStackTrace();
            return createErrorResponse(500);
        }
    }

    // ALTERAÇÃO: Converte o status para String
    private JSONObject createErrorResponse(int status) {
        return new JSONObject()
                .put("status", String.valueOf(status));
    }
}