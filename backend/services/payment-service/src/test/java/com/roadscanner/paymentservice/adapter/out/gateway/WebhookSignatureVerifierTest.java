package com.roadscanner.paymentservice.adapter.out.gateway;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookSignatureVerifierTest {

    private static final String SECRET = "test-secret";
    private static final String PAYLOAD = "{\"gatewayEventId\":\"evt-1\",\"type\":\"PAYMENT_CAPTURED\"}";

    @Test
    void verifiesAMatchingHmacSignature() {
        String signature = WebhookSignatureVerifier.sign(PAYLOAD, SECRET);
        assertThat(WebhookSignatureVerifier.verify(PAYLOAD, signature, SECRET)).isTrue();
    }

    @Test
    void rejectsATamperedPayload() {
        String signature = WebhookSignatureVerifier.sign(PAYLOAD, SECRET);
        assertThat(WebhookSignatureVerifier.verify(PAYLOAD + "x", signature, SECRET)).isFalse();
    }

    @Test
    void rejectsAWrongSecret() {
        String signature = WebhookSignatureVerifier.sign(PAYLOAD, SECRET);
        assertThat(WebhookSignatureVerifier.verify(PAYLOAD, signature, "other-secret")).isFalse();
    }

    @Test
    void rejectsMissingSignatureOrSecret() {
        assertThat(WebhookSignatureVerifier.verify(PAYLOAD, null, SECRET)).isFalse();
        assertThat(WebhookSignatureVerifier.verify(PAYLOAD, "sig", "")).isFalse();
    }
}
