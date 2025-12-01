import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.json.JSONArray;
import org.json.JSONObject;
import java.sql.SQLException;
import java.util.List;

public class MovieController {

    private final DatabaseService db;
    private final JwtService jwt;

    public MovieController() {
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

    // LISTAR_FILMES [cite: 9]
    public JSONObject listAllMovies(String token) {
        try {
            jwt.validateAndGetClaims(token);
            List<JSONObject> filmes = db.getAllMoviesAsJson();
            return new JSONObject()
                    .put("status", "200")
                    .put("mensagem", "Sucesso: Operação realizada com sucesso")
                    .put("filmes", new JSONArray(filmes));
        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (SQLException e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    // BUSCAR_FILME_ID [cite: 14]
    public JSONObject getMovieById(String token, JSONObject request) {
        try {
            jwt.validateAndGetClaims(token);

            // Verifica o nome exato da chave no protocolo (id_filme)
            String idStr = request.optString("id_filme");
            if (idStr == null || idStr.isEmpty()) {
                return createErrorResponse(400, "Erro: Operação não encontrada ou inválida"); // Ou Bad Request
            }

            int id = Integer.parseInt(idStr);

            JSONObject filme = db.findMovieByIdAsJson(id);
            if (filme == null) {
                return createErrorResponse(404, "Erro: Recurso inexistente"); // [cite: 17]
            }

            List<JSONObject> reviews = db.getReviewsByMovieId(id);

            return new JSONObject()
                    .put("status", "200")
                    .put("mensagem", "Sucesso: operação realizada com sucesso")
                    .put("filme", filme)
                    .put("reviews", new JSONArray(reviews)); // [cite: 15]

        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (NumberFormatException e) {
            return createErrorResponse(400, "Erro: Operação não encontrada ou inválida");
        } catch (SQLException e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    public JSONObject createMovie(String token, JSONObject request) {
        try {
            validateAdmin(token);
            JSONObject movieData = request.getJSONObject("filme");
            String titulo = movieData.optString("titulo");
            String diretor = movieData.optString("diretor");
            String ano = movieData.optString("ano");
            String sinopse = movieData.optString("sinopse");
            JSONArray generosArray = movieData.optJSONArray("genero");

            String error = ValidationService.validateMovie(titulo, diretor, ano, sinopse, generosArray);
            if (error != null) return createErrorResponse(405, "Erro: Campos inválidos, verifique o tipo e quantidade de caracteres"); // [cite: 4]

            db.createMovie(titulo, diretor, ano, formatGenres(generosArray), sinopse);
            return createSuccessResponse(201, "Sucesso: Recurso cadastrado");
        } catch (SecurityException e) {
            return createErrorResponse(403, "Erro: sem permissão");
        } catch (org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException e) {
            return createErrorResponse(409, "Erro: Recurso ja existe"); // [cite: 4]
        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (Exception e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    public JSONObject updateMovie(String token, JSONObject request) {
        try {
            validateAdmin(token);
            JSONObject movieData = request.getJSONObject("filme");
            int id = Integer.parseInt(movieData.getString("id"));
            String titulo = movieData.optString("titulo");
            String diretor = movieData.optString("diretor");
            String ano = movieData.optString("ano");
            String sinopse = movieData.optString("sinopse");
            JSONArray generosArray = movieData.optJSONArray("genero");

            String error = ValidationService.validateMovie(titulo, diretor, ano, sinopse, generosArray);
            if (error != null) return createErrorResponse(405, "Erro: Campos inválidos, verifique o tipo e quantidade de caracteres");

            if (db.findMovieByIdAsJson(id) == null) return createErrorResponse(404, "Erro: Recurso inexistente");

            db.updateMovie(id, titulo, diretor, ano, formatGenres(generosArray), sinopse);
            return createSuccessResponse(200, "Sucesso: operação realizada com sucesso");
        } catch (SecurityException e) {
            return createErrorResponse(403, "Erro: sem permissão");
        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (Exception e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    public JSONObject deleteMovie(String token, JSONObject request) {
        try {
            validateAdmin(token);
            int id = Integer.parseInt(request.getString("id"));
            if (db.findMovieByIdAsJson(id) == null) return createErrorResponse(404, "Erro: Recurso inexistente");

            db.deleteMovie(id);
            return createSuccessResponse(200, "Sucesso: operação realizada com sucesso");
        } catch (SecurityException e) {
            return createErrorResponse(403, "Erro: sem permissão");
        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (Exception e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    private String formatGenres(JSONArray generosArray) {
        if (generosArray == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < generosArray.length(); i++) {
            if (i > 0) sb.append(",");
            sb.append(generosArray.getString(i));
        }
        return sb.toString();
    }

    private JSONObject createErrorResponse(int status, String message) {
        return new JSONObject().put("status", String.valueOf(status)).put("mensagem", message);
    }
    private JSONObject createSuccessResponse(int status, String message) {
        return new JSONObject().put("status", String.valueOf(status)).put("mensagem", message);
    }
}