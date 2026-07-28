package com.roadscanner.paymentservice.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A refund against a captured {@link Payment} — a separate aggregate from {@code Payment} (a
 * payment can have zero, one, or several refunds over its life), exactly as {@code booking-service}
 * splits {@code SeatHold} from {@code Booking} (docs/services/payment-service/domain-model.md).
 *
 * <p>The refund <strong>amount is supplied by {@code booking-service}</strong>, never computed here
 * — {@code payment-service} executes the refund it is handed, it does not decide cancellation
 * policy (docs/architecture/service-boundaries.md). Every transition is idempotent.
 */
public final class Refund {

    private final RefundId id;
    private final PaymentId paymentId;
    private final BookingReference bookingReference;
    private final Money amount;
    private final boolean fullRefund;
    private final RefundReason reason;
    private RefundStatus status;
    private final IdempotencyKey idempotencyKey;
    private GatewayReference gatewayReference;
    private final List<RefundAttempt> attempts;
    private final Instant createdAt;
    private Instant completedAt;
    private Instant failedAt;

    private Refund(RefundId id, PaymentId paymentId, BookingReference bookingReference, Money amount,
                   boolean fullRefund, RefundReason reason, RefundStatus status, IdempotencyKey idempotencyKey,
                   GatewayReference gatewayReference, List<RefundAttempt> attempts, Instant createdAt,
                   Instant completedAt, Instant failedAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.bookingReference = bookingReference;
        this.amount = amount;
        this.fullRefund = fullRefund;
        this.reason = reason;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.gatewayReference = gatewayReference;
        this.attempts = new ArrayList<>(attempts);
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.failedAt = failedAt;
    }

    /** {@code Initiate Refund} — created in {@code REQUESTED}. Emits {@code RefundInitiated}. */
    public static Refund create(RefundId id, PaymentId paymentId, BookingReference bookingReference, Money amount,
                                boolean fullRefund, RefundReason reason, IdempotencyKey idempotencyKey, Instant now) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(paymentId, "paymentId must not be null");
        Objects.requireNonNull(bookingReference, "bookingReference must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return new Refund(id, paymentId, bookingReference, amount, fullRefund, reason, RefundStatus.REQUESTED,
                idempotencyKey, GatewayReference.none(), new ArrayList<>(), now, null, null);
    }

    public static Refund reconstitute(RefundId id, PaymentId paymentId, BookingReference bookingReference,
                                      Money amount, boolean fullRefund, RefundReason reason, RefundStatus status,
                                      IdempotencyKey idempotencyKey, GatewayReference gatewayReference,
                                      List<RefundAttempt> attempts, Instant createdAt, Instant completedAt,
                                      Instant failedAt) {
        return new Refund(id, paymentId, bookingReference, amount, fullRefund, reason, status, idempotencyKey,
                gatewayReference, attempts, createdAt, completedAt, failedAt);
    }

    /** {@code REQUESTED -> PROCESSING} — the refund request has been sent to the gateway. */
    public boolean markProcessing(GatewayReference gatewayReference, Instant now) {
        if (status != RefundStatus.REQUESTED) {
            return false;
        }
        if (gatewayReference != null) {
            this.gatewayReference = gatewayReference;
        }
        this.attempts.add(RefundAttempt.start(attempts.size() + 1,
                gatewayReference != null ? gatewayReference : GatewayReference.none(), now));
        this.status = RefundStatus.PROCESSING;
        return true;
    }

    /** {@code REQUESTED/PROCESSING -> COMPLETED}. Emits {@code RefundCompleted}. */
    public boolean complete(GatewayReference gatewayReference, Instant now) {
        if (status.isTerminal()) {
            return false;
        }
        if (gatewayReference != null) {
            this.gatewayReference = gatewayReference;
        }
        latestAttempt().ifPresent(a -> a.succeed(now));
        this.status = RefundStatus.COMPLETED;
        this.completedAt = now;
        return true;
    }

    /** {@code REQUESTED/PROCESSING -> FAILED}. Emits {@code RefundFailed} (routed to support —
     * docs/architecture/payment-flow.md). Not auto-retried indefinitely. */
    public boolean fail(String failureCode, String failureReason, Instant now) {
        if (status.isTerminal()) {
            return false;
        }
        latestAttempt().ifPresent(a -> a.fail(failureCode, failureReason, now));
        this.status = RefundStatus.FAILED;
        this.failedAt = now;
        return true;
    }

    private Optional<RefundAttempt> latestAttempt() {
        return attempts.isEmpty() ? Optional.empty() : Optional.of(attempts.get(attempts.size() - 1));
    }

    public RefundId id() {
        return id;
    }

    public PaymentId paymentId() {
        return paymentId;
    }

    public BookingReference bookingReference() {
        return bookingReference;
    }

    public Money amount() {
        return amount;
    }

    public boolean isFullRefund() {
        return fullRefund;
    }

    public RefundReason reason() {
        return reason;
    }

    public RefundStatus status() {
        return status;
    }

    public IdempotencyKey idempotencyKey() {
        return idempotencyKey;
    }

    public GatewayReference gatewayReference() {
        return gatewayReference;
    }

    public List<RefundAttempt> attempts() {
        return List.copyOf(attempts);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<Instant> completedAt() {
        return Optional.ofNullable(completedAt);
    }

    public Optional<Instant> failedAt() {
        return Optional.ofNullable(failedAt);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Refund other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
