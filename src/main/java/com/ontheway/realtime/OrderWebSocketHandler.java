package com.ontheway.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class OrderWebSocketHandler extends TextWebSocketHandler {
    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session);
    }

    public void broadcast(OrderRealtimeEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (Exception ex) {
            return;
        }
        for (WebSocketSession session : sessions) {
            if (!session.isOpen() || !canReceive(session, event)) {
                continue;
            }
            try {
                session.sendMessage(new TextMessage(payload));
            } catch (Exception ignored) {
                sessions.remove(session);
            }
        }
    }

    private boolean canReceive(WebSocketSession session, OrderRealtimeEvent event) {
        Object role = session.getAttributes().get(OrderWebSocketAuthInterceptor.ROLE);
        Object userId = session.getAttributes().get(OrderWebSocketAuthInterceptor.USER_ID);
        if (!(userId instanceof Long id) || !(role instanceof String roleName)) {
            return false;
        }
        return "ADMIN".equals(roleName)
                || event.userId().equals(id)
                || event.merchantOwnerId().equals(id);
    }
}
