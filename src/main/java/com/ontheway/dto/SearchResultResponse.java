package com.ontheway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ontheway.model.enums.StoreType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One result of a cross-shop product search: an item, its price, the shop that sells it, and the
 * distance from the customer to that shop.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchResultResponse {

    @JsonProperty("menuItemId")
    private Long menuItemId;

    @JsonProperty("itemName")
    private String itemName;

    @JsonProperty("description")
    private String description;

    @JsonProperty("price")
    private Double price;

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
}
