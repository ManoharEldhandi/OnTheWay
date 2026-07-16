package com.ontheway.payment;

import com.ontheway.model.enums.PaymentStatus;
import com.ontheway.util.Money;
import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "ontheway.payment.provider", havingValue = "stripe")
public class StripePaymentGateway implements PaymentGateway {
    private final String apiKey;
    private final String webhookSecret;

    public StripePaymentGateway(@Value("${ontheway.payment.stripe.apikey:}") String apiKey,
                                @Value("${ontheway.payment.stripe.webhook-secret:}") String webhookSecret) {
        this.apiKey = apiKey;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public String name() {
        return "stripe";
    }

    @Override
    public ChargeResult charge(Long orderId, double amount, String method, String idempotencyKey) {
        requireKey();
        try {
            Stripe.apiKey = apiKey;
            PaymentIntentCreateParams.Builder params = PaymentIntentCreateParams.builder()
                    .setAmount(Money.toMinor(amount))
                    .setCurrency(Money.DEFAULT_CURRENCY.toLowerCase())
                    .putMetadata("orderId", String.valueOf(orderId));
            if (method != null && !method.isBlank() && method.startsWith("pm_")) {
                params.setPaymentMethod(method).setConfirm(true);
            }
            PaymentIntent intent = PaymentIntent.create(params.build(), RequestOptions.builder()
                    .setIdempotencyKey(idempotencyKey).build());
            PaymentStatus status = "succeeded".equals(intent.getStatus())
                    ? PaymentStatus.COMPLETED : PaymentStatus.PENDING;
            return new ChargeResult(status == PaymentStatus.COMPLETED, intent.getId(), "stripe", status);
        } catch (Exception ex) {
            return new ChargeResult(false, "stripe_error", "stripe", PaymentStatus.FAILED);
        }
    }

    @Override
    public boolean refund(Long orderId, String gatewayReference, double amount, String idempotencyKey) {
        requireKey();
        try {
            Stripe.apiKey = apiKey;
            Refund refund = Refund.create(RefundCreateParams.builder()
                    .setPaymentIntent(gatewayReference)
                    .setAmount(Money.toMinor(amount))
                    .build(), RequestOptions.builder().setIdempotencyKey(idempotencyKey).build());
            return !"failed".equals(refund.getStatus());
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public boolean verifyWebhook(String payload, String signature) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }
        try {
            Webhook.constructEvent(payload, signature, webhookSecret);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void requireKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Stripe API key is not configured");
        }
    }
}
