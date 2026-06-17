package com.ontheway.service;

import com.ontheway.exception.UnauthorizedException;
import com.ontheway.model.RefreshToken;
import com.ontheway.model.User;
import com.ontheway.repository.RefreshTokenRepository;
import com.ontheway.repository.UserRepository;
import com.ontheway.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthTokenService {
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Value("${jwt.refreshTokenExpirationMs}")
    private long refreshTokenExpirationMs;

    @Transactional
    public String issueRefreshToken(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));
        String token = jwtTokenProvider.generateRefreshToken(email);
        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(hash(token))
                .expiresAt(LocalDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000))
                .build());
        return token;
    }

    @Transactional
    public TokenPair rotate(String refreshToken) {
        Claims claims = requireRefreshClaims(refreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new UnauthorizedException("Refresh token is not recognized"));
        LocalDateTime now = LocalDateTime.now();
        if (!stored.isActive(now)) {
            throw new UnauthorizedException("Refresh token is expired or revoked");
        }
        stored.setRevokedAt(now);
        String email = claims.getSubject();
        String access = jwtTokenProvider.generateAccessToken(email);
        String nextRefresh = issueRefreshToken(email);
        return new TokenPair(access, nextRefresh);
    }

    @Transactional
    public void revoke(String refreshToken) {
        refreshTokenRepository.findByTokenHash(hash(refreshToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(LocalDateTime.now());
            }
        });
    }

    private Claims requireRefreshClaims(String refreshToken) {
        try {
            Claims claims = jwtTokenProvider.parse(refreshToken).getBody();
            if (!"refresh".equals(claims.get("type", String.class))) {
                throw new UnauthorizedException("Expected a refresh token");
            }
            return claims;
        } catch (UnauthorizedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new UnauthorizedException("Refresh token is invalid");
        }
    }

    private String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash refresh token", ex);
        }
    }

    public record TokenPair(String accessToken, String refreshToken) {}
}
