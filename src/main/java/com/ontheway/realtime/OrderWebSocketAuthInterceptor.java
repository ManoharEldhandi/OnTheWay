package com.ontheway.realtime;

import com.ontheway.model.User;
import com.ontheway.repository.UserRepository;
import com.ontheway.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OrderWebSocketAuthInterceptor implements HandshakeInterceptor {
    public static final String USER_ID = "userId";
    public static final String ROLE = "role";
    public static final String TOKEN_EXPIRES_AT = "tokenExpiresAt";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = tokenFromQuery(request.getURI().getRawQuery());
        if (token == null || !jwtTokenProvider.validateAccessToken(token)) {
            return false;
        }
        try {
            var claims = jwtTokenProvider.parse(token).getBody();
            attributes.put(TOKEN_EXPIRES_AT, claims.getExpiration().getTime());
            return userRepository.findByEmailIgnoreCase(claims.getSubject())
                    .map(user -> attach(attributes, user))
                    .orElse(false);
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No-op.
    }

    private boolean attach(Map<String, Object> attributes, User user) {
        attributes.put(USER_ID, user.getUserId());
        attributes.put(ROLE, user.getRole().name());
        return true;
    }

    private String tokenFromQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && "token".equals(parts[0])) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
