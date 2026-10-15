package tz.ppra.ppraAMS.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class JWTService {

    @Value("${jwt.secret:gdgfgsdgfhjsggkshgksdjghjksdggdskjghuksdhgshjgsdfhdfg}")
    private String secret;

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationMs;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email, Long userId, String fullName, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userId);
        claims.put("name", fullName);
        claims.put("role", role);

        Date now = new Date();
        Date exp = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(email)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            parse(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    public boolean isTokenExpired(String token) {
        return parse(token).getExpiration().before(new Date());
    }

    public String extractEmail(String token) {
        return parse(token).getSubject();
    }

    public Long extractUserId(String token) {
        Object uid = parse(token).get("uid");
        return uid == null ? null : Long.valueOf(uid.toString());
    }

    public String extractFullName(String token) {
        return (String) parse(token).get("name");
    }

    public String extractRole(String token) {
        return (String) parse(token).get("role");
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
