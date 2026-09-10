package com.ontheway.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Keyless mock gateway: deterministically approves any positive charge and returns a
 * synthetic reference. Active by default ({@code ontheway.payment.provider=mock}). Lets the
 * commerce flow be demoed and tested without real payment credentials.
 */
@Component
@ConditionalOnProperty(name = "ontheway.payment.provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public ChargeResult charge(Long orderId, double amount, String method, String idempotencyKey) {
        // The demo UI deliberately exposes this deterministic decline so a presenter can show
        // the recovery path without relying on an external provider or a magic test card.
        boolean success = amount > 0 && !"DEMO_DECLINE".equalsIgnoreCase(method);
        String reference = "mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return new ChargeResult(success, reference, "mock");
    }

    @Override
    public boolean refund(Long orderId, String gatewayReference, double amount, String idempotencyKey) {
        return amount > 0 && gatewayReference != null && gatewayReference.startsWith("mock_");
    }

    @Override
    public boolean verifyWebhook(String payload, String signature) {
        return "mock".equals(signature);
    }
}
