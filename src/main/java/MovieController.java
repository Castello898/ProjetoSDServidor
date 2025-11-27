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

            // Extrai dados para validação
            String titulo = movieData.optString("titulo");
            String diretor = movieData.optString("diretor");
            String ano = movieData.optString("ano");
            String sinopse = movieData.optString("sinopse");
            JSONArray generosArray = movieData.optJSONArray("genero");

            // 1. VALIDAÇÃO DOS DADOS
            String error = ValidationService.validateMovie(titulo, diretor, ano, sinopse, generosArray);
            if (error != null) {
                // Retorna erro 405 (Campos inválidos) conforme padrão usado no UserController
                return createErrorResponse(405, "Erro: " + error);
            }

            // 2. FORMATAÇÃO DOS GÊNEROS (Apenas nomes separados por vírgula)
            String generosFormatados = formatGenres(generosArray);

            db.createMovie(titulo, diretor, ano, generosFormatados, sinopse);

            return createSuccessResponse(201, "Sucesso: Recurso cadastrado");

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

            int id = Integer.parseInt(movieData.getString("id"));

            // Extrai dados para validação
            String titulo = movieData.optString("titulo");
            String diretor = movieData.optString("diretor");
            String ano = movieData.optString("ano");
            String sinopse = movieData.optString("sinopse");
            JSONArray generosArray = movieData.optJSONArray("genero");

            // 1. VALIDAÇÃO DOS DADOS
            String error = ValidationService.validateMovie(titulo, diretor, ano, sinopse, generosArray);
            if (error != null) {
                return createErrorResponse(405, "Erro: " + error);
            }

            // 2. FORMATAÇÃO DOS GÊNEROS
            String generosFormatados = formatGenres(generosArray);

            db.updateMovie(id, titulo, diretor, ano, generosFormatados, sinopse);

            return createSuccessResponse(200, "Sucesso: operação realizada com sucesso");

        } catch (SecurityException e) {
            return createErrorResponse(403, "Erro: Sem permissão");
        } catch (JwtException e) {
            return createErrorResponse(401, "Erro: Token inválido");
        } catch (SQLException e) {
            return createErrorResponse(500, "Erro: Falha interna do servidor");
        }
    }

    // Método auxiliar para formatar "Ação,Comédia" sem aspas extras
    private String formatGenres(JSONArray generosArray) {
        if (generosArray == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < generosArray.length(); i++) {
            if (i > 0) sb.append(",");
            sb.append(generosArray.getString(i));
        }
        return sb.toString();
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