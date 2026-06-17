package com.ontheway.controller;

import com.ontheway.dto.*;
import com.ontheway.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/payments", "/api/v1/payments"})
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Create a new payment for the specified order.
     */
    @PreAuthorize("hasRole('USER')")
    @PostMapping
    public ResponseEntity<PaymentResponseDTO> createPayment(Authentication auth,
                                                            @Valid @RequestBody PaymentCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.createPayment(dto, auth.getName()));
    }

    /**
     * Get payment details for a given order ID.
     */
    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @GetMapping("/order/{orderId}")
    public ResponseEntity<PaymentResponseDTO> getPaymentByOrder(Authentication auth,
                                                                @PathVariable("orderId") Long orderId) {
        return ResponseEntity.ok(paymentService.getPaymentByOrderId(orderId, auth.getName()));
    }

    /**
     * Update payment status (e.g. SUCCESS, FAILED, PENDING).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{paymentId}/status")
    public ResponseEntity<?> updatePaymentStatus(@PathVariable("paymentId") Long paymentId,
                                                 @RequestParam("status") String status) {
        paymentService.updatePaymentStatus(paymentId, status);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{paymentId}/refund")
    public ResponseEntity<PaymentResponseDTO> refund(@PathVariable("paymentId") Long paymentId) {
        return ResponseEntity.ok(paymentService.refundPayment(paymentId));
    }

    @PostMapping("/webhook/{gateway}")
    public ResponseEntity<Void> webhook(@PathVariable("gateway") String gateway,
                                        @RequestHeader(value = "Stripe-Signature", required = false) String stripeSig,
                                        @RequestHeader(value = "X-Razorpay-Signature", required = false) String razorpaySig,
                                        @RequestHeader(value = "X-Mock-Signature", required = false) String mockSig,
                                        @RequestBody String payload) {
        String signature = stripeSig != null ? stripeSig : razorpaySig != null ? razorpaySig : mockSig;
        paymentService.handleWebhook(gateway, payload, signature);
        return ResponseEntity.noContent().build();
    }
}
