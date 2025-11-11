// Novo arquivo: MovieController.java
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

    // Valida o token e verifica se é ADM
    private Claims validateAdmin(String token) throws JwtException, SecurityException {
        Claims claims = jwt.validateAndGetClaims(token);
        if (!"admin".equals(claims.get("role", String.class))) {
            throw new SecurityException("Acesso negado: Requer privilégios de ADM");
        }
        return claims;
    }

    // Operação: CRIAR_FILME (ADM) [cite: 190]
    public JSONObject createMovie(String token, JSONObject request) {
        try {
            validateAdmin(token);
            JSONObject movieData = request.getJSONObject("filme");
            String generos = movieData.getJSONArray("genero").join(",");

            // (O createMovie do DB já aceita strings)
            db.createMovie(
                    movieData.getString("titulo"),
                    movieData.getString("diretor"),
                    movieData.getString("ano"),
                    generos,
                    movieData.getString("sinopse")
            );
            return createSuccessResponse(201, "Sucesso: Recurso cadastrado"); // [cite: 276]
        } catch (SecurityException e) {
            return createErrorResponse(403, "Erro: Sem permissão");
        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (SQLException e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    public JSONObject updateMovie(String token, JSONObject request) {
        try {
            validateAdmin(token);
            JSONObject movieData = request.getJSONObject("filme");

            // CONVERSÃO: String (JSON) -> int (DB)
            int id = Integer.parseInt(movieData.getString("id")); // [cite: 306]
            String generos = movieData.getJSONArray("genero").join(",");

            db.updateMovie(
                    id,
                    movieData.getString("titulo"),
                    movieData.getString("diretor"),
                    movieData.getString("ano"),
                    generos,
                    movieData.getString("sinopse")
            );
            return createSuccessResponse(200, "Sucesso: operação realizada com sucesso"); // [cite: 293]
        } catch (SecurityException e) {
            return createErrorResponse(403, "Erro: Sem permissão");
        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (SQLException e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    public JSONObject deleteMovie(String token, JSONObject request) {
        try {
            validateAdmin(token);

            // CORREÇÃO: O 'id' está fora do objeto 'filme' no protocolo [cite: 300]
            // CONVERSÃO: String (JSON) -> int (DB)
            int id = Integer.parseInt(request.getString("id"));

            db.deleteMovie(id);
            return createSuccessResponse(200, "Sucesso: operação realizada com sucesso"); // [cite: 300]
        } catch (SecurityException e) {
            return createErrorResponse(403, "Erro: sem permissão"); // [cite: 301]
        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido"); // [cite: 300]
        } catch (NumberFormatException e) {
            return createErrorResponse(400, "Erro: ID inválido"); // [cite: 307]
        } catch (SQLException e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor"); // [cite: 301]
        }
    }

    // Operação: LISTAR_FILMES
    public JSONObject listAllMovies(String token) {
        try {
            jwt.validateAndGetClaims(token);

            // Este método já retorna a lista formatada com strings
            List<JSONObject> filmes = db.getAllMoviesAsJson();

            return new JSONObject()
                    .put("status", "200")
                    .put("mensagem", "Sucesso: operação realizada com sucesso")
                    .put("filmes", new JSONArray(filmes)); // [cite: 281]
        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido"); // [cite: 283]
        } catch (SQLException e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor"); // [cite: 284]
        }
    }

    // Métodos utilitários de resposta
    private JSONObject createErrorResponse(int status, String message) {
        return new JSONObject().put("status", String.valueOf(status)).put("mensagem", message);
    }
    private JSONObject createSuccessResponse(int status, String message) {
        return new JSONObject().put("status", String.valueOf(status)).put("mensagem", message);
    }
}