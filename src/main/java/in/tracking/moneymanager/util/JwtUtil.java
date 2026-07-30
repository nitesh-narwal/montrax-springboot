package in.tracking.moneymanager.util;

import in.tracking.moneymanager.service.AppCacheService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class JwtUtil {

    private final AppCacheService appCacheService;

    public String generateToken(String email) {
        return generateToken(email, Map.of());
    }

    public String generateToken(String email, Map<String, Object> extraClaims) {
        String expirationValue  = appCacheService.get("jwt.expiration", "86400000");
        if (expirationValue == null || expirationValue.isBlank()) {
            throw new IllegalStateException("JWT expiration value is not configured or is empty");
        }
        long expiration;
        try {
            expiration = Long.parseLong(expirationValue.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException(
                    "Invalid JWT expiration value: '" + expirationValue + "'. Must be a numeric value in milliseconds.", e
            );
        }
        var builder = Jwts.builder()
                .setSubject(email)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256);

        extraClaims.forEach(builder::claim);

        return builder.compact();
    }

    private Key getSigningKey() {
        String secret = appCacheService.get("jwt.secret");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET_KEY environment variable is not set or is empty");
        }
        return Keys.hmacShaKeyFor(
                secret.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    // CLAIM EXTRACTION
    public String extractUserEmail(String jwt) {
        return extractClaim(jwt, Claims::getSubject);
    }

    public <T> T extractClaim(String jwt,
                              Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(jwt);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String jwt) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(jwt)
                .getBody();
    }

    public boolean validateToken(String jwt, UserDetails userDetails) {
        String userEmail = extractUserEmail(jwt);
        return (userEmail.equals(userDetails.getUsername()));
    }
}
