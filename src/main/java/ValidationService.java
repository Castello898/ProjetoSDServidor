import java.util.regex.Pattern;

public class ValidationService {

    // RegEx: permite apenas letras (maiúsculas/minúsculas) e números.
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");

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
}