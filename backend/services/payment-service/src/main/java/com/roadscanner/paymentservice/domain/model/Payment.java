package com.roadscanner.paymentservice.domain.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The aggregate root this service exists to own — the platform's only source of truth for a
 * payment's lifecycle (docs/services/payment-service/domain-model.md). Owns exactly the state
 * machine docs/services/payment-service/payment-state-machine.md defines; every transition method
 * below is idempotent, matching that document's "every transition is idempotent" requirement — a
 * transition attempted from a state it does not apply to is a safe no-op (returns {@code false}),
 * never an exception, since every trigger (a webhook, a redelivered event, a sweep) can be
 * delivered at-least-once.
 *
 * <p><strong>Never holds card / bank / instrument data</strong> (NFR-12) — only gateway references
 * and status (docs/services/payment-service/data-ownership.md).
 */
public final class Payment {

    private final PaymentId id;
    private final BookingReference bookingReference;
    private final java.util.UUID travelerId;
    private final Money amount;
    private final PaymentMethod method;
    private final GatewayType gatewayType;
    private GatewayReference gatewayReference;
    private PaymentStatus status;
    private final IdempotencyKey idempotencyKey;
    private final List<PaymentAttempt> attempts;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant authorizedAt;
    private Instant capturedAt;
    private Instant failedAt;
    private Instant cancelledAt;
    private Instant expiredAt;

    private Payment(PaymentId id, BookingReference bookingReference, java.util.UUID travelerId, Money amount,
                    PaymentMethod method, GatewayType gatewayType, GatewayReference gatewayReference,
                    PaymentStatus status, IdempotencyKey idempotencyKey, List<PaymentAttempt> attempts,
                    Instant expiresAt, Instant createdAt, Instant authorizedAt, Instant capturedAt,
                    Instant failedAt, Instant cancelledAt, Instant expiredAt) {
        this.id = id;
        this.bookingReference = bookingReference;
        this.travelerId = travelerId;
        this.amount = amount;
        this.method = method;
        this.gatewayType = gatewayType;
        this.gatewayReference = gatewayReference;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.attempts = new ArrayList<>(attempts);
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
        this.authorizedAt = authorizedAt;
        this.capturedAt = capturedAt;
        this.failedAt = failedAt;
        this.cancelledAt = cancelledAt;
        this.expiredAt = expiredAt;
    }

    /** {@code Initiate Payment} — a {@link Payment} is created in {@code CREATED}, before any
     * gateway transaction starts (docs/services/payment-service/payment-state-machine.md). */
    public static Payment create(PaymentId id, BookingReference bookingReference, java.util.UUID travelerId,
                                 Money amount, PaymentMethod method, GatewayType gatewayType,
                                 IdempotencyKey idempotencyKey, Instant expiresAt, Instant now) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(bookingReference, "bookingReference must not be null");
        Objects.requireNonNull(travelerId, "travelerId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(gatewayType, "gatewayType must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        Objects.requireNonNull(now, "now must not be null");
        return new Payment(id, bookingReference, travelerId, amount, method, gatewayType, GatewayReference.none(),
                PaymentStatus.CREATED, idempotencyKey, new ArrayList<>(), expiresAt, now, null, null, null, null, null);
    }

    public static Payment reconstitute(PaymentId id, BookingReference bookingReference, java.util.UUID travelerId,
                                       Money amount, PaymentMethod method, GatewayType gatewayType,
                                       GatewayReference gatewayReference, PaymentStatus status,
                                       IdempotencyKey idempotencyKey, List<PaymentAttempt> attempts,
                                       Instant expiresAt, Instant createdAt, Instant authorizedAt, Instant capturedAt,
                                       Instant failedAt, Instant cancelledAt, Instant expiredAt) {
        return new Payment(id, bookingReference, travelerId, amount, method, gatewayType, gatewayReference, status,
                idempotencyKey, attempts, expiresAt, createdAt, authorizedAt, capturedAt, failedAt, cancelledAt,
                expiredAt);
    }

    /** Records a new gateway attempt and moves {@code CREATED -> PENDING}. No-op (returns
     * {@code false}) if the payment is no longer pre-capture. */
    public boolean startAttempt(GatewayReference gatewayReference, Instant now) {
        Objects.requireNonNull(gatewayReference, "gatewayReference must not be null");
        if (status != PaymentStatus.CREATED) {
            return false;
        }
        this.gatewayReference = gatewayReference;
        this.attempts.add(PaymentAttempt.start(attempts.size() + 1, gatewayReference, now));
        this.status = PaymentStatus.PENDING;
        return true;
    }

    /** {@code CREATED/PENDING -> AUTHORIZED} (auth-then-capture methods). No external event —
     * an internal waypoint (docs/services/payment-service/events-published.md). */
    public boolean authorize(Instant now) {
        if (status != PaymentStatus.CREATED && status != PaymentStatus.PENDING) {
            return false;
        }
        this.status = PaymentStatus.AUTHORIZED;
        this.authorizedAt = now;
        return true;
    }

    /** {@code CREATED/PENDING/AUTHORIZED -> CAPTURED}. Idempotent — a duplicate capture webhook for
     * an already-{@code CAPTURED} payment is a no-op. Emits {@code PaymentCompleted}. */
    public boolean capture(GatewayReference gatewayReference, Instant now) {
        if (!status.isPreCapture()) {
            return false;
        }
        if (gatewayReference != null) {
            this.gatewayReference = gatewayReference;
        }
        latestAttempt().ifPresent(a -> a.succeed(now));
        this.status = PaymentStatus.CAPTURED;
        this.capturedAt = now;
        return true;
    }

    /** {@code CREATED/PENDING/AUTHORIZED -> FAILED}. Emits {@code PaymentFailed}. */
    public boolean fail(String failureCode, String failureReason, Instant now) {
        if (!status.isPreCapture()) {
            return false;
        }
        latestAttempt().ifPresent(a -> a.fail(failureCode, failureReason, now));
        this.status = PaymentStatus.FAILED;
        this.failedAt = now;
        return true;
    }

    /** {@code CREATED/PENDING/AUTHORIZED -> CANCELLED} (voided/abandoned before capture). Surfaces
     * to {@code booking-service} as {@code PaymentFailed} (docs/services/payment-service/domain-model.md). */
    public boolean cancel(Instant now) {
        if (!status.isPreCapture()) {
            return false;
        }
        latestAttempt().ifPresent(a -> a.fail("CANCELLED", "payment cancelled before capture", now));
        this.status = PaymentStatus.CANCELLED;
        this.cancelledAt = now;
        return true;
    }

    /** {@code CREATED/PENDING/AUTHORIZED -> EXPIRED} (no gateway outcome by {@link #expiresAt}).
     * Emits {@code PaymentTimedOut}. */
    public boolean expire(Instant now) {
        if (!status.isPreCapture()) {
            return false;
        }
        latestAttempt().ifPresent(a -> a.timeOut(now));
        this.status = PaymentStatus.EXPIRED;
        this.expiredAt = now;
        return true;
    }

    /** Whether this payment has passed its acceptable window with no gateway outcome — drives the
     * {@code Sweep Expired Payments} transition to {@code EXPIRED}. */
    public boolean isExpiredAsOf(Instant now) {
        return status.isPreCapture() && !now.isBefore(expiresAt);
    }

    /** {@code CAPTURED -> REFUND_PENDING} — a full refund has been initiated. No-op unless currently
     * {@code CAPTURED}. Partial refunds leave the payment {@code CAPTURED}
     * (docs/services/payment-service/payment-state-machine.md's "Partial Refunds"). */
    public boolean beginFullRefund() {
        if (status != PaymentStatus.CAPTURED) {
            return false;
        }
        this.status = PaymentStatus.REFUND_PENDING;
        return true;
    }

    /** {@code REFUND_PENDING -> REFUNDED}. */
    public boolean completeFullRefund() {
        if (status != PaymentStatus.REFUND_PENDING) {
            return false;
        }
        this.status = PaymentStatus.REFUNDED;
        return true;
    }

    /** {@code REFUND_PENDING -> CAPTURED} — the full refund failed; money did not move back. */
    public boolean revertFullRefund() {
        if (status != PaymentStatus.REFUND_PENDING) {
            return false;
        }
        this.status = PaymentStatus.CAPTURED;
        return true;
    }

    public boolean isOwnedBy(java.util.UUID travelerId) {
        return this.travelerId.equals(travelerId);
    }

    private Optional<PaymentAttempt> latestAttempt() {
        return attempts.isEmpty() ? Optional.empty() : Optional.of(attempts.get(attempts.size() - 1));
    }

    public PaymentId id() {
        return id;
    }

    public BookingReference bookingReference() {
        return bookingReference;
    }

    public java.util.UUID travelerId() {
        return travelerId;
    }

    public Money amount() {
        return amount;
    }

    public PaymentMethod method() {
        return method;
    }

    public GatewayType gatewayType() {
        return gatewayType;
    }

    public GatewayReference gatewayReference() {
        return gatewayReference;
    }

    public PaymentStatus status() {
        return status;
    }

    public IdempotencyKey idempotencyKey() {
        return idempotencyKey;
    }

    public List<PaymentAttempt> attempts() {
        return List.copyOf(attempts);
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Optional<Instant> authorizedAt() {
        return Optional.ofNullable(authorizedAt);
    }

    public Optional<Instant> capturedAt() {
        return Optional.ofNullable(capturedAt);
    }

    public Optional<Instant> failedAt() {
        return Optional.ofNullable(failedAt);
    }

    public Optional<Instant> cancelledAt() {
        return Optional.ofNullable(cancelledAt);
    }

    public Optional<Instant> expiredAt() {
        return Optional.ofNullable(expiredAt);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Payment other && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
