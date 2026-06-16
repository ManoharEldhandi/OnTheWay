package com.ontheway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ontheway.model.enums.StoreType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A store returned by discovery, annotated with its distance and travel time
 * from the customer's current position.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoreDiscoveryResponse {

    @JsonProperty("merchantId")
    private Long merchantId;

    @JsonProperty("storeName")
    private String storeName;

    @JsonProperty("storeType")
    private StoreType storeType;

    @JsonProperty("address")
    private String address;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("distanceKm")
    private Double distanceKm;

    @JsonProperty("travelMins")
    private Integer travelMins;

    @JsonProperty("prepTimeMins")
    private Integer prepTimeMins;
}
