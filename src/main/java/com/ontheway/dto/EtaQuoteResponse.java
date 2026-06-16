package com.ontheway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ETA quote response: travel details plus the synchronized prep-start and ready times.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EtaQuoteResponse {

    @JsonProperty("merchantId")
    private Long merchantId;

    @JsonProperty("distanceKm")
    private Double distanceKm;

    @JsonProperty("travelMins")
    private Integer travelMins;

    @JsonProperty("prepTimeMins")
    private Integer prepTimeMins;

    @JsonProperty("bufferMins")
    private Integer bufferMins;

    @JsonProperty("prepStartAt")
    private LocalDateTime prepStartAt;

    @JsonProperty("readyAt")
    private LocalDateTime readyAt;
}
