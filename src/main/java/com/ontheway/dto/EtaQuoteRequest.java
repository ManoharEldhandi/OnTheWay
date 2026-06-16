package com.ontheway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request for an ETA quote: the customer's current position plus the target store.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtaQuoteRequest {

    @JsonProperty("merchantId")
    @NotNull(message = "Merchant ID is required")
    private Long merchantId;

    @JsonProperty("latitude")
    @NotNull(message = "Latitude is required")
    private Double latitude;

    @JsonProperty("longitude")
    @NotNull(message = "Longitude is required")
    private Double longitude;
}
