package com.roadscanner.paymentservice.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RefundTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private Refund newRefund() {
        return Refund.create(RefundId.generate(), PaymentId.generate(), new BookingReference(UUID.randomUUID()),
                new Money(BigDecimal.valueOf(500), Currency.getInstance("INR")), true, RefundReason.TRAVELER_REQUESTED,
                new IdempotencyKey("refund-key-1"), T0);
    }

    @Test
    void createStartsRequested() {
        Refund refund = newRefund();
        assertThat(refund.status()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(refund.isFullRefund()).isTrue();
    }

    @Test
    void processingThenCompleted() {
        Refund refund = newRefund();
        assertThat(refund.markProcessing(GatewayReference.ofRefund("r1"), T0)).isTrue();
        assertThat(refund.status()).isEqualTo(RefundStatus.PROCESSING);
        assertThat(refund.complete(GatewayReference.ofRefund("r1"), T0.plusSeconds(5))).isTrue();
        assertThat(refund.status()).isEqualTo(RefundStatus.COMPLETED);
        assertThat(refund.attempts().get(0).outcome()).isEqualTo(AttemptOutcome.SUCCEEDED);
    }

    @Test
    void failureIsTerminalAndNotRetriedAutomatically() {
        Refund refund = newRefund();
        refund.markProcessing(GatewayReference.ofRefund("r1"), T0);
        assertThat(refund.fail("REJECTED", "gateway rejected", T0.plusSeconds(5))).isTrue();
        assertThat(refund.status()).isEqualTo(RefundStatus.FAILED);
        assertThat(refund.complete(null, T0.plusSeconds(10))).isFalse();
    }

    @Test
    void completeIsIdempotent() {
        Refund refund = newRefund();
        refund.markProcessing(GatewayReference.ofRefund("r1"), T0);
        refund.complete(null, T0.plusSeconds(5));
        assertThat(refund.complete(null, T0.plusSeconds(9))).isFalse();
        assertThat(refund.completedAt()).contains(T0.plusSeconds(5));
    }
}
