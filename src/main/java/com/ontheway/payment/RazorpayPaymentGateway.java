package com.ontheway.payment;

import com.ontheway.model.enums.PaymentStatus;
import com.ontheway.util.Money;
import com.razorpay.RazorpayClient;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Component
@ConditionalOnProperty(name = "ontheway.payment.provider", havingValue = "razorpay")
public class RazorpayPaymentGateway implements PaymentGateway {
    private final String apiKey;
    private final String apiSecret;

    public RazorpayPaymentGateway(@Value("${ontheway.payment.razorpay.apikey:}") String apiKey,
                                  @Value("${ontheway.payment.razorpay.apisecret:}") String apiSecret) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    @Override
    public ChargeResult charge(Long orderId, double amount, String method, String idempotencyKey) {
        requireKeys();
        try {
            RazorpayClient client = new RazorpayClient(apiKey, apiSecret);
            JSONObject options = new JSONObject();
            options.put("amount", Money.toMinor(amount));
            options.put("currency", Money.DEFAULT_CURRENCY);
            options.put("receipt", "order-" + orderId);
            options.put("payment_capture", 1);
            com.razorpay.Order gatewayOrder = client.orders.create(options);
            return new ChargeResult(false, gatewayOrder.get("id"), "razorpay", PaymentStatus.PENDING);
        } catch (Exception ex) {
            return new ChargeResult(false, "razorpay_error", "razorpay", PaymentStatus.FAILED);
        }
    }

    @Override
    public boolean refund(Long orderId, String gatewayReference, double amount, String idempotencyKey) {
        requireKeys();
        try {
            RazorpayClient client = new RazorpayClient(apiKey, apiSecret);
            JSONObject options = new JSONObject();
            options.put("amount", Money.toMinor(amount));
            client.payments.refund(gatewayReference, options);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public boolean verifyWebhook(String payload, String signature) {
        if (apiSecret == null || apiSecret.isBlank() || signature == null || signature.isBlank()) {
            return false;
        }
        return hmacSha256(payload, apiSecret).equals(signature);
    }

    private void requireKeys() {
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            throw new IllegalStateException("Razorpay credentials are not configured");
        }
    }

    private String hmacSha256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not verify Razorpay webhook signature", ex);
        }
    }
}
