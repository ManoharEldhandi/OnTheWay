package com.ontheway.service;

import com.ontheway.dto.*;

public interface PaymentService {
    PaymentResponseDTO createPayment(PaymentCreateDTO dto, String callerEmail);
    PaymentResponseDTO getPaymentByOrderId(Long orderId, String callerEmail);
    void updatePaymentStatus(Long paymentId, String status);
    PaymentResponseDTO refundPayment(Long paymentId);
    void handleWebhook(String gateway, String payload, String signature);
}
