package com.ontheway.service;

import com.ontheway.dto.*;

public interface PaymentService {
    PaymentResponseDTO createPayment(PaymentCreateDTO dto, String callerEmail);
    PaymentProviderConfigResponse paymentProviderConfig();
    PaymentResponseDTO getPaymentByOrderId(Long orderId, String callerEmail);
    void updatePaymentStatus(Long paymentId, String status);
    PaymentResponseDTO refundPayment(Long paymentId);
    /** Internal order-cancellation operation. No-op when no completed payment exists. */
    void refundCompletedPaymentForOrder(Long orderId);
    void handleWebhook(String gateway, String payload, String signature);
}
