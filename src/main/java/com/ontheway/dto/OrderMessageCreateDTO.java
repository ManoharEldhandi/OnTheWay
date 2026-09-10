package com.ontheway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OrderMessageCreateDTO {
    @NotBlank(message = "Message cannot be blank")
    @Size(max = 600, message = "Message must be at most 600 characters")
    private String body;
}
