package com.ontheway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
public class MenuItemCreateDTO {
    @JsonProperty("name")
    @NotBlank(message = "Name is required")
    @Size(max = 150, message = "Name must be at most 150 characters")
    private String name;

    @JsonProperty("description")
    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

    @JsonProperty("price")
    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "Price must be positive")
    @DecimalMax(value = "10000000.0", message = "Price must not exceed 10000000")
    private Double price;

    @JsonProperty("availability")
    @NotNull(message = "Availability is required")
    private Boolean availability;
}
