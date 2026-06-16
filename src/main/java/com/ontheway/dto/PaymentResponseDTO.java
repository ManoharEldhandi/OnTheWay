package com.ontheway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ontheway.model.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponseDTO {
    @JsonProperty("paymentId")
    private Long paymentId;

    @JsonProperty("orderId")
    private Long orderId;

    @JsonProperty("paymentStatus")
    private PaymentStatus paymentStatus;

    @JsonProperty("paymentMethod")
    private String paymentMethod;

    @JsonProperty("amount")
    private Double amount;

    @JsonProperty("gateway")
    private String gateway;

    @JsonProperty("gatewayReference")
    private String gatewayReference;

    @JsonProperty("paymentTime")
    private LocalDateTime paymentTime;
}
