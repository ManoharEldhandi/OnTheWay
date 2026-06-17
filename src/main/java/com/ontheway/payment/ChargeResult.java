package com.ontheway.payment;

import com.ontheway.model.enums.PaymentStatus;

/**
 * The result of attempting to charge a payment through a gateway.
 *
 * @param success   whether the charge succeeded
 * @param reference the gateway's reference/intent id for this charge
 * @param gateway   the provider name (mock, stripe, razorpay)
 * @param status    the platform payment status to persist
 */
public record ChargeResult(boolean success, String reference, String gateway, PaymentStatus status) {
	public ChargeResult(boolean success, String reference, String gateway) {
		this(success, reference, gateway, success ? PaymentStatus.COMPLETED : PaymentStatus.FAILED);
	}
}
