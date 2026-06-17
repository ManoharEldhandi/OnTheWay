package com.ontheway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Reason supplied by an administrator when rejecting or suspending a shop.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ModerationReasonDTO {

    @JsonProperty("reason")
    @NotBlank(message = "A reason is required")
    @Size(max = 500, message = "Reason must be at most 500 characters")
    private String reason;
}
