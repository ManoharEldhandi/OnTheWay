package com.ontheway.payment;

/**
 * The result of attempting to charge a payment through a gateway.
 *
 * @param success   whether the charge succeeded
 * @param reference the gateway's reference/intent id for this charge
 * @param gateway   the provider name (mock, stripe, razorpay)
 */
public record ChargeResult(boolean success, String reference, String gateway) {
}
