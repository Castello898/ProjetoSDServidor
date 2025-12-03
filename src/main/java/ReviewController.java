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

    /**
     * Operação: CRIAR_REVIEW
     * Cria uma nova avaliação para um filme.
     */
    public JSONObject createReview(String token, JSONObject request) {
        try {
            // Validação de Token
            Claims claims = jwt.validateAndGetClaims(token);

            // --- CORREÇÃO AQUI ---
            // ANTES (ERRADO): int userId = Integer.parseInt(claims.getId());
            // O "getId()" pega o 'jti' (token id), mas nós salvamos como claim customizada "id".
            int userId = claims.get("id", Integer.class);
            // ---------------------

            String username = claims.getSubject();

            JSONObject reviewData = request.getJSONObject("review");

            // Validação de campos obrigatórios (JSON)
            if (!reviewData.has("id_filme") || !reviewData.has("nota") || !reviewData.has("titulo")) {
                return createErrorResponse(422, "Erro: Chaves faltantes ou invalidas");
            }

            int idFilme = Integer.parseInt(reviewData.getString("id_filme"));

            // Valida existência do filme
            if (db.findMovieByIdAsJson(idFilme) == null) {
                return createErrorResponse(404, "Erro: Recurso inexistente");
            }

            int nota = Integer.parseInt(reviewData.getString("nota"));
            String titulo = reviewData.getString("titulo");
            String descricao = reviewData.optString("descricao", "");

            // --- VALIDAÇÕES DE REGRA DE NEGÓCIO ---

            if (titulo.length() > 50) {
                return createErrorResponse(405, "Erro: Campos inválidos, verifique o tipo e quantidade de caracteres");
            }

            if (descricao.length() > 250) {
                return createErrorResponse(405, "Erro: Campos inválidos, verifique o tipo e quantidade de caracteres");
            }

            if (nota < 0 || nota > 5) {
                return createErrorResponse(405, "Erro: Campos inválidos, verifique o tipo e quantidade de caracteres");
            }

            // Formata a data atual
            String data = new SimpleDateFormat("dd/MM/yyyy").format(new Date());

            // Tenta salvar no banco
            db.createReview(idFilme, userId, username, nota, titulo, descricao, data);

            return createSuccessResponse(201, "Sucesso: Recurso cadastrado");

        } catch (org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException e) {
            return createErrorResponse(409, "Erro: Recurso ja existe");
        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (NumberFormatException e) {
            // É aqui que o erro estava caindo quando claims.getId() retornava null
            return createErrorResponse(400, "Erro: Operação não encontrada ou inválida");
        } catch (Exception e) {
            e.printStackTrace();
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    /**
     * Operação: LISTAR_REVIEWS_USUARIO
     */
    public JSONObject listUserReviews(String token) {
        try {
            Claims claims = jwt.validateAndGetClaims(token);

            // --- CORREÇÃO AQUI ---
            int userId = claims.get("id", Integer.class);
            // ---------------------

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

    /**
     * Operação: EDITAR_REVIEW
     */
    public JSONObject updateReview(String token, JSONObject request) {
        try {
            Claims claims = jwt.validateAndGetClaims(token);

            // --- CORREÇÃO AQUI ---
            int userId = claims.get("id", Integer.class);
            // ---------------------

            JSONObject reviewData = request.getJSONObject("review");

            if (!reviewData.has("id") || !reviewData.has("nota") || !reviewData.has("titulo")) {
                return createErrorResponse(422, "Erro: Chaves faltantes ou invalidas");
            }

            int idReview = Integer.parseInt(reviewData.getString("id"));

            DatabaseService.Review currentReview = db.findReviewById(idReview);
            if (currentReview == null) {
                return createErrorResponse(404, "Erro: Recurso inexistente");
            }

            if (currentReview.idUsuario != userId) {
                return createErrorResponse(403, "Erro: sem permissão");
            }

            int nota = Integer.parseInt(reviewData.getString("nota"));
            String titulo = reviewData.getString("titulo");
            String descricao = reviewData.optString("descricao", "");

            if (titulo.length() > 50 || descricao.length() > 250 || nota < 0 || nota > 5) {
                return createErrorResponse(405, "Erro: Campos inválidos, verifique o tipo e quantidade de caracteres");
            }

            String data = new SimpleDateFormat("dd/MM/yyyy").format(new Date());

            db.updateReview(idReview, nota, titulo, descricao, data);

            return createSuccessResponse(200, "Sucesso: operação realizada com sucesso");

        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (NumberFormatException e) {
            return createErrorResponse(400, "Erro: Operação não encontrada ou inválida");
        } catch (Exception e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    /**
     * Operação: EXCLUIR_REVIEW
     */
    public JSONObject deleteReview(String token, JSONObject request) {
        try {
            Claims claims = jwt.validateAndGetClaims(token);

            // --- CORREÇÃO AQUI ---
            int userId = claims.get("id", Integer.class);
            // ---------------------

            String role = claims.get("role", String.class);

            if (!request.has("id")) {
                return createErrorResponse(400, "Erro: Operação não encontrada ou inválida");
            }

            int idReview = Integer.parseInt(request.getString("id"));

            DatabaseService.Review currentReview = db.findReviewById(idReview);
            if (currentReview == null) {
                return createErrorResponse(404, "Erro: Recurso inexistente");
            }

            // Permissão: Dono da review OU Admin
            if (currentReview.idUsuario != userId && !"admin".equals(role)) {
                return createErrorResponse(403, "Erro: sem permissão");
            }

            db.deleteReview(idReview);
            return createSuccessResponse(200, "Sucesso: operação realizada com sucesso");

        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (NumberFormatException e) {
            return createErrorResponse(400, "Erro: Operação não encontrada ou inválida");
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