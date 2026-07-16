package com.ontheway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderCreateDTO {
    @JsonProperty("merchantId")
    @NotNull(message = "Merchant ID is required")
    private Long merchantId;

    // Optional: if the customer's live location is provided (and the store has a
    // location), pickup time is ETA-synchronized and this field is ignored.
    @JsonProperty("pickupTime")
    private LocalDateTime pickupTime;

    // Optional live location for ETA synchronization.
    @JsonProperty("latitude")
    @DecimalMin(value = "-90.0", message = "Latitude must be at least -90")
    @DecimalMax(value = "90.0", message = "Latitude must be at most 90")
    private Double latitude;

    @JsonProperty("longitude")
    @DecimalMin(value = "-180.0", message = "Longitude must be at least -180")
    @DecimalMax(value = "180.0", message = "Longitude must be at most 180")
    private Double longitude;

    @JsonProperty("items")
    @Valid
    @NotEmpty(message = "At least one item is required")
    @Size(max = 100, message = "An order can contain at most 100 line items")
    private List<OrderItemCreateDTO> items;

    @JsonProperty("paymentMethod")
    @NotBlank(message = "Payment method is required")
    @Size(max = 255, message = "Payment method must be at most 255 characters")
    private String paymentMethod;
}
