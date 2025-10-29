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
                    // ATUALIZAÇÃO: Envia a mensagem de erro específica
                    return createErrorResponse(400, "Operação desconhecida: " + operacao);
            }

        } catch (JSONException e) {
            // ATUALIZAÇÃO: Envia a mensagem de erro específica
            return createErrorResponse(400, "Requisição JSON mal formatada ou operação faltando.");
        } catch (Exception e) {
            e.printStackTrace();
            // ATUALIZAÇÃO: Envia a mensagem de erro específica
            return createErrorResponse(500, "Erro interno inesperado no servidor: " + e.getMessage());
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