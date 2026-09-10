package com.ontheway.controller;

import com.ontheway.dto.OrderMessageCreateDTO;
import com.ontheway.dto.OrderMessageResponseDTO;
import com.ontheway.service.OrderMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/orders", "/api/v1/orders"})
@RequiredArgsConstructor
public class OrderMessageController {
    private final OrderMessageService orderMessageService;

    @PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
    @GetMapping("/{orderId}/messages")
    public ResponseEntity<List<OrderMessageResponseDTO>> list(
            Authentication auth, @PathVariable Long orderId) {
        return ResponseEntity.ok(orderMessageService.list(orderId, auth.getName()));
    }

    @PreAuthorize("hasAnyRole('USER', 'MERCHANT')")
    @PostMapping("/{orderId}/messages")
    public ResponseEntity<OrderMessageResponseDTO> send(
            Authentication auth, @PathVariable Long orderId,
            @Valid @RequestBody OrderMessageCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderMessageService.send(orderId, dto.getBody(), auth.getName()));
    }
}
