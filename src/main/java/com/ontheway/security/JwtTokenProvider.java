package com.ontheway.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.accessTokenExpirationMs}")
    private long accessTokenExpirationMs;

    @Value("${jwt.refreshTokenExpirationMs}")
    private long refreshTokenExpirationMs;

    private Key key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** Issues a short-lived access token for the authenticated principal. */
    public String generateToken(Authentication authentication) {
        return buildToken(authentication.getName().toLowerCase(), accessTokenExpirationMs, "access");
    }

    /** Issues a long-lived refresh token. */
    public String generateRefreshToken(String username) {
        return buildToken(username.toLowerCase(), refreshTokenExpirationMs, "refresh");
    }

    private String buildToken(String subject, long ttlMs, String type) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlMs);
        return Jwts.builder()
                .setSubject(subject)
                .claim("type", type)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key)
                .compact();
    }

    /**
     * Validates signature and expiry, then returns the parsed claims.
     * Throws a {@link JwtException} for any invalid token — callers must handle it.
     */
    public Jws<Claims> parse(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
    }

    public String getUsernameFromJWT(String token) {
        return parse(token).getBody().getSubject();
    }

    /** Returns true only if the token's signature is valid and it has not expired. */
    public boolean validateToken(String token) {
        try {
            parse(token);
            return true;
        } catch (ExpiredJwtException ex) {
            log.debug("Expired JWT: {}", ex.getMessage());
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Invalid JWT: {}", ex.getMessage());
        }
        return false;
    }
}
