package com.ontheway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A live position update sent by the customer's app while en route to a pickup.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationUpdateDTO {

    @JsonProperty("latitude")
    @NotNull(message = "Latitude is required")
    private Double latitude;

    @JsonProperty("longitude")
    @NotNull(message = "Longitude is required")
    private Double longitude;
}
