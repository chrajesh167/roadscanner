package com.roadscanner.paymentservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roadscanner.paymentservice.adapter.out.security.EphemeralJwtKeyPair;
import com.roadscanner.paymentservice.config.PaymentProperties;
import com.roadscanner.paymentservice.domain.model.Role;
import com.roadscanner.paymentservice.testsupport.TestcontainersConfiguration;
import com.roadscanner.paymentservice.testsupport.security.TestJwtIssuer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Full HTTP-surface flow against real Postgres and Kafka (Testcontainers). Proves the whole chain:
 * JWT-authenticated payment initiation &rarr; a signature-verified gateway webhook capturing the
 * payment &rarr; a {@code PaymentCompleted} published to {@code payment-events} in the exact shape
 * {@code booking-service}'s frozen consumer expects (field names {@code eventType}/{@code bookingId}/
 * {@code paymentReference}) &rarr; an internal refund &rarr; a refund webhook completing it. The
 * gateway is the in-process Razorpay stub; no external SDK is called.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PaymentServiceEndToEndTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private EphemeralJwtKeyPair ephemeralJwtKeyPair;

    @Autowired
    private PaymentProperties paymentProperties;

    @Autowired
    private KafkaConnectionDetails kafkaConnectionDetails;

    @Value("${roadscanner.security.jwt.issuer}")
    private String issuer;

    @Test
    void fullPaymentThenRefundLifecycle() throws Exception {
        TestJwtIssuer issuer = new TestJwtIssuer(ephemeralJwtKeyPair, this.issuer);
        UUID traveler = UUID.randomUUID();
        UUID bookingReference = UUID.randomUUID();
        String travelerToken = issuer.issue(traveler, Role.TRAVELER);

        // 1. Initiate payment (client-facing, JWT).
        String initiateBody = """
                {"bookingReference":"%s","amount":500.00,"currency":"INR","method":"UPI","gatewayType":"RAZORPAY"}
                """.formatted(bookingReference);
        ResponseEntity<String> initiated = rest.exchange("/api/v1/payments", org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(initiateBody, bearerJson(travelerToken, "idem-pay-1")), String.class);
        assertThat(initiated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode initiatedJson = JSON.readTree(initiated.getBody());
        String paymentId = initiatedJson.get("paymentId").asText();
        assertThat(initiatedJson.get("status").asText()).isEqualTo("PENDING");

        // 2. Gateway webhook: capture (signature-verified, no JWT).
        String gatewayPaymentId = "razorpay-pay-" + paymentId;
        String captureWebhook = """
                {"gatewayEventId":"evt-cap-1","type":"PAYMENT_CAPTURED","gatewayPaymentId":"%s"}
                """.formatted(gatewayPaymentId).trim();
        ResponseEntity<String> captureAck = postWebhook("RAZORPAY", captureWebhook, "RAZORPAY");
        assertThat(captureAck.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(JSON.readTree(captureAck.getBody()).get("outcome").asText()).isEqualTo("APPLIED");

        // 3. Status is now CAPTURED.
        ResponseEntity<String> status = rest.exchange("/api/v1/payments/" + paymentId + "/status",
                org.springframework.http.HttpMethod.GET, new HttpEntity<>(bearer(travelerToken)), String.class);
        assertThat(JSON.readTree(status.getBody()).get("status").asText()).isEqualTo("CAPTURED");

        // 4. PaymentCompleted was published to payment-events in booking-service's frozen shape.
        ConsumerRecord<String, String> event = consumeOne(paymentProperties.kafka().paymentEventsTopic());
        JsonNode eventJson = JSON.readTree(event.value());
        assertThat(eventJson.get("eventType").asText()).isEqualTo("COMPLETED");
        assertThat(eventJson.get("bookingId").asText()).isEqualTo(bookingReference.toString());
        assertThat(eventJson.get("paymentReference").asText()).isEqualTo(paymentId);

        // 5. Initiate a full refund (internal, service-to-service, no JWT).
        String refundBody = "{\"reason\":\"TRAVELER_REQUESTED\"}";
        ResponseEntity<String> refundInitiated = rest.exchange(
                "/internal/api/v1/payments/" + paymentId + "/refunds", org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(refundBody, jsonWithIdempotency("idem-refund-1")), String.class);
        assertThat(refundInitiated.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String refundId = JSON.readTree(refundInitiated.getBody()).get("refundId").asText();

        // 6. Refund webhook: completed.
        String refundWebhook = """
                {"gatewayEventId":"evt-ref-1","type":"REFUND_COMPLETED","gatewayRefundId":"razorpay-refund-%s"}
                """.formatted(refundId).trim();
        ResponseEntity<String> refundAck = postWebhook("RAZORPAY", refundWebhook, "RAZORPAY");
        assertThat(refundAck.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 7. Refund COMPLETED and payment REFUNDED.
        ResponseEntity<String> refund = rest.exchange(
                "/internal/api/v1/payments/" + paymentId + "/refunds/" + refundId,
                org.springframework.http.HttpMethod.GET, HttpEntity.EMPTY, String.class);
        assertThat(JSON.readTree(refund.getBody()).get("status").asText()).isEqualTo("COMPLETED");

        ResponseEntity<String> finalPayment = rest.exchange("/api/v1/payments/" + paymentId,
                org.springframework.http.HttpMethod.GET, new HttpEntity<>(bearer(travelerToken)), String.class);
        assertThat(JSON.readTree(finalPayment.getBody()).get("status").asText()).isEqualTo("REFUNDED");
    }

    @Test
    void webhookWithABadSignatureIsRejected() {
        String webhook = "{\"gatewayEventId\":\"evt-x\",\"type\":\"PAYMENT_CAPTURED\",\"gatewayPaymentId\":\"nope\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Webhook-Signature", "not-a-valid-signature");
        ResponseEntity<String> response = rest.exchange("/webhooks/RAZORPAY",
                org.springframework.http.HttpMethod.POST, new HttpEntity<>(webhook, headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unauthenticatedPaymentInitiationIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "idem-unauth");
        ResponseEntity<String> response = rest.exchange("/api/v1/payments",
                org.springframework.http.HttpMethod.POST,
                new HttpEntity<>("{\"bookingReference\":\"" + UUID.randomUUID()
                        + "\",\"amount\":100,\"currency\":\"INR\",\"method\":\"UPI\"}", headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ResponseEntity<String> postWebhook(String gatewayType, String payload, String secretKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Webhook-Signature", hmac(payload, paymentProperties.gateways().webhookSecretFor(secretKey)));
        return rest.exchange("/webhooks/" + gatewayType, org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(payload, headers), String.class);
    }

    private HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private HttpHeaders bearerJson(String token, String idempotencyKey) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        return headers;
    }

    private HttpHeaders jsonWithIdempotency(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        return headers;
    }

    private String hmac(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private ConsumerRecord<String, String> consumeOne(String topic) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaConnectionDetails.getConsumerBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "e2e-verifier-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            var found = new java.util.concurrent.atomic.AtomicReference<ConsumerRecord<String, String>>();
            await().atMost(Duration.ofSeconds(20)).until(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    found.set(record);
                    return true;
                }
                return false;
            });
            return found.get();
        }
    }
}
