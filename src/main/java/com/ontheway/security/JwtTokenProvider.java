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
import java.util.UUID;

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
        return generateAccessToken(authentication.getName());
    }

    /** Issues a short-lived access token for a known username/email. */
    public String generateAccessToken(String username) {
        return buildToken(username.toLowerCase(), accessTokenExpirationMs, "access");
    }

    /** Issues a long-lived refresh token. */
    public String generateRefreshToken(String username) {
        return buildToken(username.toLowerCase(), refreshTokenExpirationMs, "refresh");
    }

    public long getAccessTokenExpirationMs() {
        return accessTokenExpirationMs;
    }

    private String buildToken(String subject, long ttlMs, String type) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlMs);
        return Jwts.builder()
                .setSubject(subject)
            .setId(UUID.randomUUID().toString())
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

    /** Returns true only if the token is a valid, unexpired access token. */
    public boolean validateAccessToken(String token) {
        return validateTokenType(token, "access");
    }

    /** Returns true only if the token is a valid, unexpired refresh token. */
    public boolean validateRefreshToken(String token) {
        return validateTokenType(token, "refresh");
    }

    /** Returns true only if the token's signature is valid and it has not expired. */
    public boolean validateToken(String token) {
        return parseSafely(token) != null;
    }

    private boolean validateTokenType(String token, String expectedType) {
        Claims claims = parseSafely(token);
        return claims != null && expectedType.equals(claims.get("type", String.class));
    }

    private Claims parseSafely(String token) {
        try {
            return parse(token).getBody();
        } catch (ExpiredJwtException ex) {
            log.debug("Expired JWT: {}", ex.getMessage());
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Invalid JWT: {}", ex.getMessage());
        }
        return null;
    }
}
