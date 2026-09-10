package com.ontheway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Code read by the merchant from the customer's ready-for-pickup screen. */
@Data
public class PickupConfirmDTO {
    @NotBlank(message = "Pickup code is required")
    @Size(max = 12, message = "Pickup code is invalid")
    private String pickupCode;
}
