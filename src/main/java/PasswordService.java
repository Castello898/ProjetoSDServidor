import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class PasswordService {

    // Usa SHA-256 para hashing (melhor que MD5, mais simples que BCrypt)
    public static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            // Converte bytes para hexadecimal
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            // Em um app real, isso não deveria acontecer.
            throw new RuntimeException("Erro: Algoritmo SHA-256 não encontrado.", e);
        }
    }

    public static boolean checkPassword(String plainPassword, String hashedPassword) {
        String hashOfPlain = hashPassword(plainPassword);
        return hashOfPlain.equals(hashedPassword);
    }
}