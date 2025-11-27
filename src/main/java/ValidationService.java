import java.util.regex.Pattern;
import org.json.JSONArray;

public class ValidationService {

    // RegEx: permite apenas letras (maiúsculas/minúsculas) e números.
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final Pattern DIGITS_ONLY = Pattern.compile("^[0-9]+$");
    /**
     * Valida login e senha conforme os requisitos 
     * @return Uma string de erro se for inválido, ou null se for válido.
     */
    public static String validateCredentials(String username, String password) {
        if (username.length() < 3 || username.length() > 20) {
            return "Login deve ter entre 3 e 20 caracteres."; // [cite: 77]
        }
        if (password.length() < 3 || password.length() > 20) {
            return "Senha deve ter entre 3 e 20 caracteres."; // [cite: 78]
        }
        if (!ALPHANUMERIC_PATTERN.matcher(username).matches()) {
            return "Login deve conter apenas letras e números."; // [cite: 76]
        }
        if (!ALPHANUMERIC_PATTERN.matcher(password).matches()) {
            return "Senha deve conter apenas letras e números."; // [cite: 76]
        }
        return null; // Válido
    }
    public static String validateMovie(String titulo, String diretor, String ano, String sinopse, JSONArray generos) {
        // Título: min 3, max 30
        if (titulo == null || titulo.trim().length() < 3 || titulo.trim().length() > 30) {
            return "Título deve ter entre 3 e 30 caracteres.";
        }

        // Diretor: min 3, max 30
        if (diretor == null || diretor.trim().length() < 3 || diretor.trim().length() > 30) {
            return "Diretor deve ter entre 3 e 30 caracteres.";
        }

        // Ano: min 3, max 4 e APENAS DÍGITOS
        if (ano == null || ano.length() < 3 || ano.length() > 4 || !DIGITS_ONLY.matcher(ano).matches()) {
            return "Ano deve conter apenas dígitos (3 a 4 caracteres).";
        }

        // Gêneros: Pelo menos um selecionado
        if (generos == null || generos.isEmpty()) {
            return "Selecione pelo menos um gênero.";
        }

        // Sinopse: max 250
        if (sinopse != null && sinopse.length() > 250) {
            return "Sinopse deve ter no máximo 250 caracteres.";
        }

        return null; // Dados válidos
    }
}