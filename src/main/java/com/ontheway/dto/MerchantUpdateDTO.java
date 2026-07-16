package com.ontheway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantUpdateDTO {
    @JsonProperty("storeName")
    @NotBlank(message = "Store name cannot be blank")
    @Size(max = 150, message = "Store name must be at most 150 characters")
    private String storeName;

    @JsonProperty("address")
    @NotBlank(message = "Address cannot be blank")
    @Size(max = 300, message = "Address must be at most 300 characters")
    private String address;

    @JsonProperty("latitude")
    @DecimalMin(value = "-90.0", message = "Latitude must be at least -90")
    @DecimalMax(value = "90.0", message = "Latitude must be at most 90")
    private Double latitude;

    @JsonProperty("longitude")
    @DecimalMin(value = "-180.0", message = "Longitude must be at least -180")
    @DecimalMax(value = "180.0", message = "Longitude must be at most 180")
    private Double longitude;

    @JsonProperty("prepTimeMins")
    @Min(value = 0, message = "Prep time cannot be negative")
    @Max(value = 1440, message = "Prep time must be at most 1440 minutes")
    private Integer prepTimeMins;

    @JsonProperty("etaBufferMins")
    @NotNull(message = "ETA buffer is required")
    @Min(value = 1, message = "ETA buffer must be positive")
    @Max(value = 1440, message = "ETA buffer must be at most 1440 minutes")
    private Integer etaBufferMins;
}
