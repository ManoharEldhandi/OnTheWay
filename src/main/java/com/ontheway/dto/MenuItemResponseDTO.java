package com.ontheway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItemResponseDTO {
    @JsonProperty("menuItemId")
    private Long menuItemId;

    @JsonProperty("merchantId")
    private Long merchantId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("price")
    private Double price;

    @JsonProperty("priceMinor")
    private Long priceMinor;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("availability")
    private Boolean availability;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;
}
