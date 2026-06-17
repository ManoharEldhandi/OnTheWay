package com.ontheway.payment;

/**
 * Processes payments through some provider.
 *
 * with a mock implementation. The default {@link MockPaymentGateway} is keyless and
 * deterministic (auto-confirms) so the full order-to-payment flow runs and is tested without
 * real payment credentials. Real Stripe/Razorpay implementations (SDKs already on the
 * classpath) can be added without changing callers.
 */
public interface PaymentGateway {

    /**
     * Charge {@code amount} for the given order.
     *
     * @param orderId        the order being paid for
     * @param amount         amount to charge
     * @param method         payment method label (e.g. CARD, UPI)
     * @param idempotencyKey a client/server key that must make retries safe
     */
    ChargeResult charge(Long orderId, double amount, String method, String idempotencyKey);

    default boolean refund(Long orderId, String gatewayReference, double amount, String idempotencyKey) {
        throw new UnsupportedOperationException("Refunds are not supported by this payment gateway");
    }

    default boolean verifyWebhook(String payload, String signature) {
        return false;
    }
}
