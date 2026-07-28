package com.roadscanner.paymentservice.adapter.out.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * The signature-verification abstraction shared by the stub gateway adapters. Computes an
 * HMAC-SHA256 of the raw payload under the gateway's signing secret and compares it, in constant
 * time, to the signature the gateway sent. Real gateway SDKs each have their own signing scheme;
 * this stub stands in until a real SDK is integrated (docs/services/payment-service/domain-model.md's
 * "Webhook Verification" — "Use stub verification where real gateway SDKs are unavailable").
 */
final class WebhookSignatureVerifier {

    private WebhookSignatureVerifier() {
    }

    static String sign(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable in this JVM", e);
        }
    }

    static boolean verify(String payload, String signatureHeader, String secret) {
        if (signatureHeader == null || secret == null || secret.isBlank()) {
            return false;
        }
        String expected = sign(payload, secret);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8));
    }
}
