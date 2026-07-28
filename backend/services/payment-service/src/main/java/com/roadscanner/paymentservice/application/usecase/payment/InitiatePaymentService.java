package com.roadscanner.paymentservice.application.usecase.payment;

import com.roadscanner.paymentservice.domain.model.GatewayReference;
import com.roadscanner.paymentservice.domain.model.Payment;
import com.roadscanner.paymentservice.domain.model.PaymentId;
import com.roadscanner.paymentservice.domain.port.in.InitiatePayment;
import com.roadscanner.paymentservice.domain.port.out.PaymentEventPublisher;
import com.roadscanner.paymentservice.domain.port.out.PaymentGateway;
import com.roadscanner.paymentservice.domain.port.out.PaymentGatewayRegistry;
import com.roadscanner.paymentservice.domain.port.out.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Implements {@link InitiatePayment}. Idempotent on the client key, and enforces "at most one
 * non-terminal {@code Payment} per booking" (FR-4.3): a repeat of the same key, or a booking that
 * already has a live payment, returns the existing payment rather than starting a second gateway
 * transaction (docs/services/payment-service/use-cases.md).
 */
public class InitiatePaymentService implements InitiatePayment {

    private static final Logger log = LoggerFactory.getLogger(InitiatePaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayRegistry gatewayRegistry;
    private final PaymentEventPublisher eventPublisher;
    private final Clock clock;
    private final Duration paymentWindow;

    public InitiatePaymentService(PaymentRepository paymentRepository, PaymentGatewayRegistry gatewayRegistry,
                                  PaymentEventPublisher eventPublisher, Clock clock, Duration paymentWindow) {
        this.paymentRepository = paymentRepository;
        this.gatewayRegistry = gatewayRegistry;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.paymentWindow = paymentWindow;
    }

    @Override
    public Result initiate(Command command) {
        Optional<Payment> existingByKey = paymentRepository.findByIdempotencyKey(command.idempotencyKey());
        if (existingByKey.isPresent()) {
            Payment existing = existingByKey.get();
            return new Result(existing.id(), existing.status(), true);
        }

        Optional<Payment> activeForBooking = paymentRepository.findActiveByBookingReference(command.bookingReference());
        if (activeForBooking.isPresent()) {
            Payment existing = activeForBooking.get();
            log.info("Booking {} already has a non-terminal payment {} — returning it",
                    command.bookingReference(), existing.id());
            return new Result(existing.id(), existing.status(), true);
        }

        Instant now = clock.instant();
        Payment payment = Payment.create(PaymentId.generate(), command.bookingReference(), command.travelerId(),
                command.amount(), command.method(), command.gatewayType(), command.idempotencyKey(),
                now.plus(paymentWindow), now);

        PaymentGateway gateway = gatewayRegistry.resolve(command.gatewayType());
        GatewayReference gatewayReference = gateway.initiateCharge(new PaymentGateway.ChargeRequest(
                payment.id(), payment.amount(), payment.method(), payment.bookingReference()));

        payment.startAttempt(gatewayReference, now);
        Payment saved = paymentRepository.save(payment);
        eventPublisher.publishPaymentCreated(saved, now);
        log.info("Initiated payment {} for booking {} via {}", saved.id(), saved.bookingReference(),
                saved.gatewayType());
        return new Result(saved.id(), saved.status(), false);
    }
}
