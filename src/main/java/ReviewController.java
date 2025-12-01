import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.json.JSONArray;
import org.json.JSONObject;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ReviewController {

    private final DatabaseService db;
    private final JwtService jwt;

    public ReviewController() {
        this.db = DatabaseService.getInstance();
        this.jwt = new JwtService();
    }

    public JSONObject createReview(String token, JSONObject request) {
        try {
            Claims claims = jwt.validateAndGetClaims(token);
            int userId = Integer.parseInt(claims.getId());
            String username = claims.getSubject();

            JSONObject reviewData = request.getJSONObject("review");
            int idFilme = Integer.parseInt(reviewData.getString("id_filme"));

            // Valida existencia do filme
            if (db.findMovieByIdAsJson(idFilme) == null) {
                return createErrorResponse(404, "Erro: Recurso inexistente"); // [cite: 6]
            }

            int nota = Integer.parseInt(reviewData.getString("nota"));
            String titulo = reviewData.getString("titulo");
            String descricao = reviewData.getString("descricao");

            if (nota < 0 || nota > 5) return createErrorResponse(405, "Erro: Campos inválidos, verifique o tipo e quantidade de caracteres");

            String data = new SimpleDateFormat("dd/MM/yyyy").format(new Date());

            db.createReview(idFilme, userId, username, nota, titulo, descricao, data);

            return createSuccessResponse(201, "Sucesso: Recurso cadastrado"); // [cite: 5]

        } catch (org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException e) {
            return createErrorResponse(409, "Erro: Recurso ja existe");
        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (Exception e) {
            e.printStackTrace();
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    // LISTAR_REVIEWS_USUARIO [cite: 12]
    public JSONObject listUserReviews(String token) {
        try {
            Claims claims = jwt.validateAndGetClaims(token);
            int userId = Integer.parseInt(claims.getId());

            List<JSONObject> reviews = db.getReviewsByUserId(userId);

            return new JSONObject()
                    .put("status", "200")
                    .put("mensagem", "Sucesso: Operação realizada com sucesso")
                    .put("reviews", new JSONArray(reviews));

        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (Exception e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    public JSONObject updateReview(String token, JSONObject request) {
        try {
            Claims claims = jwt.validateAndGetClaims(token);
            int userId = Integer.parseInt(claims.getId());

            JSONObject reviewData = request.getJSONObject("review");
            int idReview = Integer.parseInt(reviewData.getString("id"));

            DatabaseService.Review currentReview = db.findReviewById(idReview);
            if (currentReview == null) return createErrorResponse(404, "Erro: Recurso inexistente");

            if (currentReview.idUsuario != userId) {
                return createErrorResponse(403, "Erro: sem permissão");
            }

            int nota = Integer.parseInt(reviewData.getString("nota"));
            if (nota < 0 || nota > 5) return createErrorResponse(405, "Erro: Campos inválidos, verifique o tipo e quantidade de caracteres");

            String titulo = reviewData.getString("titulo");
            String descricao = reviewData.getString("descricao");
            String data = new SimpleDateFormat("dd/MM/yyyy").format(new Date());

            db.updateReview(idReview, nota, titulo, descricao, data);

            return createSuccessResponse(200, "Sucesso: operação realizada com sucesso");

        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (Exception e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    public JSONObject deleteReview(String token, JSONObject request) {
        try {
            Claims claims = jwt.validateAndGetClaims(token);
            int userId = Integer.parseInt(claims.getId());
            String role = claims.get("role", String.class);
            int idReview = Integer.parseInt(request.getString("id"));

            DatabaseService.Review currentReview = db.findReviewById(idReview);
            if (currentReview == null) return createErrorResponse(404, "Erro: Recurso inexistente"); // [cite: 29]

            if (currentReview.idUsuario != userId && !"admin".equals(role)) {
                return createErrorResponse(403, "Erro: sem permissão");
            }

            db.deleteReview(idReview);
            return createSuccessResponse(200, "Sucesso: operação realizada com sucesso");

        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (Exception e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    private JSONObject createErrorResponse(int status, String message) {
        return new JSONObject().put("status", String.valueOf(status)).put("mensagem", message);
    }
    private JSONObject createSuccessResponse(int status, String message) {
        return new JSONObject().put("status", String.valueOf(status)).put("mensagem", message);
    }
}