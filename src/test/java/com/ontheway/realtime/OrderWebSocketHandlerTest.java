package com.ontheway.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ontheway.model.enums.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderWebSocketHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void sendsOnlyToAuthorizedUnexpiredSessions() throws Exception {
        OrderWebSocketHandler handler = new OrderWebSocketHandler(objectMapper);
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(OrderWebSocketAuthInterceptor.USER_ID, 10L);
        attributes.put(OrderWebSocketAuthInterceptor.ROLE, "USER");
        attributes.put(OrderWebSocketAuthInterceptor.TOKEN_EXPIRES_AT,
                System.currentTimeMillis() + 60_000);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session);

        handler.broadcast(event(10L));

        verify(session).sendMessage(any(TextMessage.class));
        verify(session, never()).close(any(CloseStatus.class));
    }

    @Test
    void closesExpiredSessionsBeforeBroadcasting() throws Exception {
        OrderWebSocketHandler handler = new OrderWebSocketHandler(objectMapper);
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(OrderWebSocketAuthInterceptor.USER_ID, 10L);
        attributes.put(OrderWebSocketAuthInterceptor.ROLE, "USER");
        attributes.put(OrderWebSocketAuthInterceptor.TOKEN_EXPIRES_AT,
                System.currentTimeMillis() - 1);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(session);

        handler.broadcast(event(10L));

        verify(session, never()).sendMessage(any());
        verify(session).close(argThat(status ->
                status.getCode() == CloseStatus.POLICY_VIOLATION.getCode()));
    }

    private OrderRealtimeEvent event(Long userId) {
        return new OrderRealtimeEvent(
                "ORDER_STATUS_CHANGED",
                1L,
                userId,
                2L,
                3L,
                OrderStatus.READY,
                LocalDateTime.of(2026, 7, 16, 12, 0),
                "ON_TIME");
    }
}
