import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.JwtException;

import javax.crypto.SecretKey;
import java.util.Date;

public class JwtService {

    // Chave secreta para assinar o token. NUNCA exponha isso publicamente.
    private static final SecretKey SECRET_KEY = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private static final long EXPIRATION_TIME_MS = 3600_000; // 1 hora

    /**
     * Gera um token JWT para um usuário. [cite: 33]
     */
    public String generateToken(int id, String username, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + EXPIRATION_TIME_MS);

        return Jwts.builder()
                .setSubject(username) // [cite: 35]
                .claim("id", id)      // [cite: 34]
                .claim("role", role)  // [cite: 36]
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(SECRET_KEY)
                .compact();
    }

    /**
     * Valida o token e retorna os "claims" (dados) contidos nele.
     * Lança JwtException se o token for inválido, expirado ou malformado.
     */
    public Claims validateAndGetClaims(String token) {
        if (token == null) {
            throw new JwtException("Token não fornecido");
        }

        return Jwts.parserBuilder()
                .setSigningKey(SECRET_KEY)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}