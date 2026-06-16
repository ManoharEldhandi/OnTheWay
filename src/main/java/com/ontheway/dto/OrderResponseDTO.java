package com.ontheway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ontheway.model.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponseDTO {
    @JsonProperty("orderId")
    private Long orderId;

    @JsonProperty("userId")
    private Long userId;

    @JsonProperty("merchantId")
    private Long merchantId;

    @JsonProperty("orderTime")
    private LocalDateTime orderTime;

    @JsonProperty("pickupTime")
    private LocalDateTime pickupTime;

    @JsonProperty("etaSegment")
    private String etaSegment;

    @JsonProperty("status")
    private OrderStatus status;

    @JsonProperty("totalAmount")
    private Double totalAmount;

    @JsonProperty("items")
    private List<OrderItemResponseDTO> items;

    @JsonProperty("payment")
    private PaymentResponseDTO payment;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;
}
