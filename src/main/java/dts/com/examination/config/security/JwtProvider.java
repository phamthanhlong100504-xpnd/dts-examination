package dts.com.examination.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.UUID;

@Component
public class JwtProvider {

    private final SecretKey accessKey;

    public JwtProvider(JwtProperties jwtProperties) {
        this.accessKey = Keys.hmacShaKeyFor(decodeSecret(jwtProperties.getSecret()));
    }

    private static byte[] decodeSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret must not be empty");
        }
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(secret);
        } catch (Exception e1) {
            try {
                bytes = Decoders.BASE64URL.decode(secret);
            } catch (Exception e2) {
                bytes = secret.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        if (bytes.length < 32) {
            try {
                java.security.MessageDigest sha256 = java.security.MessageDigest.getInstance("SHA-256");
                bytes = sha256.digest(bytes);
            } catch (java.security.NoSuchAlgorithmException e) {
                byte[] padded = new byte[32];
                for (int i = 0; i < 32; i++) {
                    padded[i] = bytes[i % bytes.length];
                }
                bytes = padded;
            }
        }
        return bytes;
    }

    public Claims validateAccessToken(String token) {
        return Jwts.parser()
                .verifyWith(accessKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public UUID getUserId(Claims claims) {
        return UUID.fromString(claims.getSubject());
    }

    @SuppressWarnings("unchecked")
    public List<String> getRoles(Claims claims) {
        return claims.get("roles", List.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getPermissions(Claims claims) {
        return claims.get("permissions", List.class);
    }
}
