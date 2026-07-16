package com.ontheway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ontheway.model.enums.VegNonVeg;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PreferenceUpdateDTO {
    @JsonProperty("vegNonVeg")
    private VegNonVeg vegNonVeg;

    @JsonProperty("favoriteCuisine")
    @Size(max = 100, message = "Favorite cuisine must be at most 100 characters")
    private String favoriteCuisine;
}
