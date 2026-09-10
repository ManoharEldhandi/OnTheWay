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

    /** Pickup-shop details let the customer render an order-specific live route. */
    @JsonProperty("merchantName")
    private String merchantName;

    @JsonProperty("merchantLatitude")
    private Double merchantLatitude;

    @JsonProperty("merchantLongitude")
    private Double merchantLongitude;

    /** The customer's checkout route origin; never included in merchant queue UI. */
    @JsonProperty("customerLatitude")
    private Double customerLatitude;

    @JsonProperty("customerLongitude")
    private Double customerLongitude;

    @JsonProperty("orderTime")
    private LocalDateTime orderTime;

    @JsonProperty("pickupTime")
    private LocalDateTime pickupTime;

    @JsonProperty("prepStartAt")
    private LocalDateTime prepStartAt;

    @JsonProperty("etaSegment")
    private String etaSegment;

    @JsonProperty("status")
    private OrderStatus status;

    @JsonProperty("totalAmount")
    private Double totalAmount;

    @JsonProperty("totalAmountMinor")
    private Long totalAmountMinor;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("pickupCode")
    private String pickupCode;

    @JsonProperty("items")
    private List<OrderItemResponseDTO> items;

    @JsonProperty("payment")
    private PaymentResponseDTO payment;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;
}
