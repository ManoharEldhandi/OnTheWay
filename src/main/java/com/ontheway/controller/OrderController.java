package com.ontheway.controller;

import com.ontheway.dto.*;
import com.ontheway.repository.UserRepository;
import com.ontheway.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final UserRepository userRepository;

    @PreAuthorize("hasRole('USER')")
    @PostMapping
    public ResponseEntity<OrderResponseDTO> placeOrder(Authentication auth,
                                                       @Valid @RequestBody OrderCreateDTO dto) {
        Long userId = extractUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.placeOrder(userId, dto));
    }

    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponseDTO> getOrder(Authentication auth,
                                                     @PathVariable("orderId") Long orderId) {
        return ResponseEntity.ok(orderService.getOrderById(orderId, auth.getName()));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/user")
    public ResponseEntity<List<OrderResponseDTO>> getOrdersForUser(Authentication auth) {
        Long userId = extractUserId(auth);
        return ResponseEntity.ok(orderService.getOrdersByUser(userId));
    }

    @PreAuthorize("hasRole('MERCHANT')")
    @GetMapping("/merchant")
    public ResponseEntity<List<OrderResponseDTO>> getOrdersForMerchant(Authentication auth) {
        return ResponseEntity.ok(orderService.getOrdersForOwner(auth.getName()));
    }

    @PreAuthorize("hasRole('MERCHANT')")
    @PutMapping("/{orderId}/status")
    public ResponseEntity<OrderResponseDTO> updateOrderStatus(
            Authentication auth,
            @PathVariable("orderId") Long orderId,
            @RequestParam("status") String status) {
        return ResponseEntity.ok(orderService.updateOrderStatus(orderId, status, auth.getName()));
    }

    private Long extractUserId(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email))
                .getUserId();
    }
}
