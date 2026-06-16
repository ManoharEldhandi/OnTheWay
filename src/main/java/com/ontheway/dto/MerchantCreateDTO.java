package com.ontheway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ontheway.model.enums.StoreType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantCreateDTO {
    @JsonProperty("storeName")
    @NotBlank(message = "Store name is required")
    private String storeName;

    @JsonProperty("storeType")
    @NotNull(message = "Store type is required")
    private StoreType storeType;

    @JsonProperty("address")
    @NotBlank(message = "Address is required")
    private String address;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("prepTimeMins")
    @Min(value = 0, message = "Prep time cannot be negative")
    private Integer prepTimeMins;

    @JsonProperty("etaBufferMins")
    @NotNull(message = "ETA buffer is required")
    @Min(value = 1, message = "ETA buffer must be positive")
    private Integer etaBufferMins;
}
