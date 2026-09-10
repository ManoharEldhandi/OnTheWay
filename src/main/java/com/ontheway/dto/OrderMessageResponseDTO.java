package com.ontheway.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class OrderMessageResponseDTO {
    private Long orderMessageId;
    private Long orderId;
    private Long senderUserId;
    private String senderName;
    private String senderRole;
    private String body;
    private LocalDateTime createdAt;
}
