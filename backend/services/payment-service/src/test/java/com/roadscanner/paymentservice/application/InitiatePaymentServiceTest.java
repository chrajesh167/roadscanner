package com.roadscanner.paymentservice.application;

import com.roadscanner.paymentservice.application.usecase.payment.InitiatePaymentService;
import com.roadscanner.paymentservice.domain.model.BookingReference;
import com.roadscanner.paymentservice.domain.model.GatewayType;
import com.roadscanner.paymentservice.domain.model.IdempotencyKey;
import com.roadscanner.paymentservice.domain.model.Money;
import com.roadscanner.paymentservice.domain.model.PaymentMethod;
import com.roadscanner.paymentservice.domain.model.PaymentStatus;
import com.roadscanner.paymentservice.domain.port.in.InitiatePayment;
import com.roadscanner.paymentservice.testsupport.fakes.FakePaymentGateway;
import com.roadscanner.paymentservice.testsupport.fakes.InMemoryPaymentRepository;
import com.roadscanner.paymentservice.testsupport.fakes.RecordingPaymentEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class InitiatePaymentServiceTest {

    private static final Instant T0 = Instant.parse("2026-08-01T00:00:00Z");

    private InMemoryPaymentRepository payments;
    private RecordingPaymentEventPublisher publisher;
    private InitiatePaymentService service;

    @BeforeEach
    void setUp() {
        payments = new InMemoryPaymentRepository();
        publisher = new RecordingPaymentEventPublisher();
        service = new InitiatePaymentService(payments, new FakePaymentGateway(), publisher,
                Clock.fixed(T0, ZoneOffset.UTC), Duration.ofMinutes(10));
    }

    private InitiatePayment.Command command(UUID booking, String key) {
        return new InitiatePayment.Command(new BookingReference(booking), UUID.randomUUID(),
                new Money(BigDecimal.valueOf(500), Currency.getInstance("INR")), PaymentMethod.UPI,
                FakePaymentGateway.TYPE, new IdempotencyKey(key));
    }

    @Test
    void initiatingStartsAPendingPaymentAndPublishesCreated() {
        InitiatePayment.Result result = service.initiate(command(UUID.randomUUID(), "key-1"));

        assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.alreadyExisted()).isFalse();
        assertThat(publisher.published).anyMatch(e -> e.startsWith("PaymentCreated"));
        assertThat(payments.findById(result.paymentId())).isPresent();
    }

    @Test
    void repeatingTheSameIdempotencyKeyReturnsTheExistingPayment() {
        UUID booking = UUID.randomUUID();
        InitiatePayment.Result first = service.initiate(command(booking, "key-1"));
        InitiatePayment.Result second = service.initiate(command(booking, "key-1"));

        assertThat(second.alreadyExisted()).isTrue();
        assertThat(second.paymentId()).isEqualTo(first.paymentId());
    }

    @Test
    void aSecondPaymentForABookingWithALivePaymentReturnsTheExistingOne() {
        UUID booking = UUID.randomUUID();
        InitiatePayment.Result first = service.initiate(command(booking, "key-1"));
        InitiatePayment.Result second = service.initiate(command(booking, "key-2"));

        assertThat(second.alreadyExisted()).isTrue();
        assertThat(second.paymentId()).isEqualTo(first.paymentId());
    }
}
