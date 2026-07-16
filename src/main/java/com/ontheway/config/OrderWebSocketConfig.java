package com.ontheway.config;

import com.ontheway.realtime.OrderWebSocketAuthInterceptor;
import com.ontheway.realtime.OrderWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class OrderWebSocketConfig implements WebSocketConfigurer {
    private final OrderWebSocketHandler handler;
    private final OrderWebSocketAuthInterceptor authInterceptor;

    @Value("${ontheway.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
        if (origins.length == 0 || Arrays.asList(origins).contains("*")) {
            throw new IllegalStateException(
                    "ontheway.cors.allowed-origins must contain explicit origins, never '*'");
        }
        registry.addHandler(handler, "/ws/orders")
                .addInterceptors(authInterceptor)
                .setAllowedOrigins(origins);
    }
}
