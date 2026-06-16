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
    public ChargeResult charge(Long orderId, double amount, String method, String idempotencyKey) {
        boolean success = amount > 0;
        String reference = "mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return new ChargeResult(success, reference, "mock");
    }
}
