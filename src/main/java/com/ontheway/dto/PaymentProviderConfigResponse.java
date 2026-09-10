package com.ontheway.dto;

/** Public, browser-safe information required to launch a configured provider checkout. */
public record PaymentProviderConfigResponse(String provider, boolean demo, String publicKey) {
}
