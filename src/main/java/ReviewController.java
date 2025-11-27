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

    // Operação: CRIAR_REVIEW (Usuário Comum)
    public JSONObject createReview(String token, JSONObject request) {
        try {
            Claims claims = jwt.validateAndGetClaims(token);
            int userId = Integer.parseInt(claims.getId());
            String username = claims.getSubject();

            JSONObject reviewData = request.getJSONObject("review");

            // Validações básicas
            int idFilme = Integer.parseInt(reviewData.getString("id_filme"));
            int nota = Integer.parseInt(reviewData.getString("nota"));
            String titulo = reviewData.getString("titulo");
            String descricao = reviewData.getString("descricao");

            if (nota < 0 || nota > 5) {
                return createErrorResponse(405, "Erro: Nota deve ser entre 0 e 5");
            }

            // Data atual automática se não vier
            String data = new SimpleDateFormat("dd/MM/yyyy").format(new Date());

            db.createReview(idFilme, userId, username, nota, titulo, descricao, data);

            return createSuccessResponse(201, "Sucesso: Review criada");

        } catch (org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException e) {
            return createErrorResponse(409, "Erro: Você já avaliou este filme");
        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (Exception e) {
            e.printStackTrace();
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    // Operação: LISTAR_REVIEWS (Público/Autenticado)
    public JSONObject listReviews(String token, JSONObject request) {
        try {
            jwt.validateAndGetClaims(token); // Requer login conforme protocolo? Geralmente sim.

            int idFilme = Integer.parseInt(request.getString("id_filme"));

            List<JSONObject> reviews = db.getReviewsByMovieId(idFilme);

            return new JSONObject()
                    .put("status", "200")
                    .put("mensagem", "Sucesso")
                    .put("reviews", new JSONArray(reviews));

        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (Exception e) {
            return createErrorResponse(500, "Erro: Falha interna");
        }
    }

    // Operação: EDITAR_REVIEW (Apenas o próprio usuário)
    public JSONObject updateReview(String token, JSONObject request) {
        try {
            Claims claims = jwt.validateAndGetClaims(token);
            int userId = Integer.parseInt(claims.getId());

            JSONObject reviewData = request.getJSONObject("review");
            int idReview = Integer.parseInt(reviewData.getString("id"));

            // Verifica existência e posse
            DatabaseService.Review currentReview = db.findReviewById(idReview);
            if (currentReview == null) return createErrorResponse(404, "Erro: Review não encontrada");

            if (currentReview.idUsuario != userId) {
                return createErrorResponse(403, "Erro: Você só pode editar suas próprias reviews");
            }

            int nota = Integer.parseInt(reviewData.getString("nota"));
            if (nota < 0 || nota > 5) return createErrorResponse(405, "Erro: Nota inválida");

            String titulo = reviewData.getString("titulo");
            String descricao = reviewData.getString("descricao");
            String data = new SimpleDateFormat("dd/MM/yyyy").format(new Date());

            db.updateReview(idReview, nota, titulo, descricao, data);

            return createSuccessResponse(200, "Sucesso: Review atualizada");

        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (Exception e) {
            return createErrorResponse(500, "Erro: Falha interna");
        }
    }

    // Operação: EXCLUIR_REVIEW (Próprio usuário OU Admin)
    public JSONObject deleteReview(String token, JSONObject request) {
        try {
            Claims claims = jwt.validateAndGetClaims(token);
            int userId = Integer.parseInt(claims.getId());
            String role = claims.get("role", String.class);

            int idReview = Integer.parseInt(request.getString("id"));

            DatabaseService.Review currentReview = db.findReviewById(idReview);
            if (currentReview == null) return createErrorResponse(404, "Erro: Review não encontrada");

            // Permite se for o dono OU se for admin
            if (currentReview.idUsuario != userId && !"admin".equals(role)) {
                return createErrorResponse(403, "Erro: Sem permissão para excluir esta review");
            }

            db.deleteReview(idReview);

            return createSuccessResponse(200, "Sucesso: Review excluída");

        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (Exception e) {
            return createErrorResponse(500, "Erro: Falha interna");
        }
    }

    private JSONObject createErrorResponse(int status, String message) {
        return new JSONObject().put("status", String.valueOf(status)).put("mensagem", message);
    }

    private JSONObject createSuccessResponse(int status, String message) {
        return new JSONObject().put("status", String.valueOf(status)).put("mensagem", message);
    }
}