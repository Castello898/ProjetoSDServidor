import org.json.JSONException;
import org.json.JSONObject;

public class RequestRouter {

    private final UserController userController;
    private final MovieController movieController;
    private final ReviewController reviewController;

    public RequestRouter() {
        this.userController = new UserController();
        this.movieController = new MovieController();
        this.reviewController = new ReviewController();
    }

    public JSONObject handleRequest(String jsonRequestString) {
        try {
            JSONObject request = new JSONObject(jsonRequestString);
            String operacao = request.optString("operacao");
            if(operacao.isEmpty()) return createErrorResponse(400, "Erro: Operação não encontrada ou inválida");

            String token = request.optString("token", null);

            switch (operacao) {
                // Usuário/Auth
                case "LOGIN": return userController.login(request);
                case "CRIAR_USUARIO": return userController.register(request);
                case "LOGOUT": return userController.logout(token);
                case "LISTAR_PROPRIO_USUARIO": return userController.viewProfile(token);
                case "EDITAR_PROPRIO_USUARIO": return userController.updatePassword(token, request);
                case "EXCLUIR_PROPRIO_USUARIO": return userController.deleteAccount(token);

                // Admin Usuário
                case "LISTAR_USUARIOS": return userController.listAllUsers(token);
                case "ADMIN_EDITAR_USUARIO": return userController.updateOtherUserPassword(token, request);
                case "ADMIN_EXCLUIR_USUARIO": return userController.deleteOtherUser(token, request);

                // Filmes
                case "CRIAR_FILME": return movieController.createMovie(token, request);
                case "EDITAR_FILME": return movieController.updateMovie(token, request);
                case "EXCLUIR_FILME": return movieController.deleteMovie(token, request);
                case "LISTAR_FILMES": return movieController.listAllMovies(token);
                case "BUSCAR_FILME_ID": return movieController.getMovieById(token, request); // [NOVO]

                // Reviews
                case "CRIAR_REVIEW": return reviewController.createReview(token, request);
                case "LISTAR_REVIEWS_USUARIO": return reviewController.listUserReviews(token); // [NOVO]
                case "EDITAR_REVIEW": return reviewController.updateReview(token, request);
                case "EXCLUIR_REVIEW": return reviewController.deleteReview(token, request);

                default:
                    return createErrorResponse(400, "Erro: Operação não encontrada ou inválida");
            }

        } catch (JSONException e) {
            return createErrorResponse(422, "Erro: Chaves faltantes ou invalidas"); // [cite: 1]
        } catch (Exception e) {
            e.printStackTrace();
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    private JSONObject createErrorResponse(int status, String message) {
        JSONObject response = new JSONObject().put("status", String.valueOf(status));
        if (message != null) response.put("mensagem", message);
        return response;
    }
}