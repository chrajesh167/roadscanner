# Booking Service — Sequence Diagrams

Seven flows: seat selection, hold, booking creation, payment success, payment failure/timeout,
traveler-initiated cancellation, and trip-cancellation cascade. Each corresponds directly to a row
in `use-cases.md`.

## 1. Seat Selection View (Composing Catalog + Live Status)

```mermaid
sequenceDiagram
    participant T as Traveler
    participant BS as booking-service
    participant IS as inventory-service
    participant PIS as provider-integration-service

    T->>BS: view seats for trip
    BS->>IS: GET trip metadata + SeatLayout + ProviderMapping
    IS-->>BS: catalog shape
    BS->>PIS: authenticate (or reuse session) for providerType
    BS->>PIS: GET live seat map for providerTripId
    PIS-->>BS: live per-seat status
    BS->>BS: compose SeatLayout shape + live status
    BS-->>T: unified seat-selection view
```

Identical to `docs/services/inventory-service/sequence-diagrams.md` flow 4 — reproduced here as
this service's own record of the same flow, since it is `booking-service`'s use case, not
`inventory-service`'s. `inventory-service` is not involved beyond the first call.

## 2. Hold Seats

```mermaid
sequenceDiagram
    participant T as Traveler
    participant BS as booking-service
    participant IS as inventory-service
    participant PIS as provider-integration-service

    T->>BS: select seat(s), proceed
    BS->>IS: re-validate trip is still bookable
    alt trip has no ProviderMapping
        BS-->>T: cannot be booked (see overview.md ambiguity #2)
    else trip is bookable
        BS->>PIS: block seat(s) for the resolved ProviderMapping
        PIS-->>BS: reservation reference + expiresAt
        BS->>BS: persist SeatHold (traveler, trip, provider identity, reference, expiresAt)
        BS-->>T: hold confirmed (reference, expiresAt)
    end
```

Matches `docs/services/inventory-service/sequence-diagrams.md` flow 5, with the addition of the
re-validation step and the local `SeatHold` persistence — flow 5 stops at "hold confirmed" because
that document is `inventory-service`'s own record of the boundary, not the full flow.

## 3. Create Booking

```mermaid
sequenceDiagram
    participant T as Traveler
    participant BS as booking-service
    participant K as Kafka

    T->>BS: submit passenger details + hold reference
    BS->>BS: look up SeatHold by reference + traveler
    alt hold not found or already consumed
        BS-->>T: invalid or already-used hold
    else expiresAt has passed
        BS-->>T: hold expired — re-select seats (FR-3.4)
    else hold still valid
        BS->>BS: create Booking (PENDING_PAYMENT), consume SeatHold
        BS->>K: publish BookingCreated
        BS-->>T: booking created, proceed to payment
    end
```

The "still valid" check is a **local comparison against `expiresAt`**, not a fresh call to
`provider-integration-service` — see `boundaries.md`'s "Known Gap: No Read-Only
Reservation-Status Check" for why no such call exists to make.

## 4. Payment Success → Confirmation

```mermaid
sequenceDiagram
    participant P as payment-service (future)
    participant K as Kafka
    participant BS as booking-service
    participant PIS as provider-integration-service

    P->>K: PaymentCompleted
    K->>BS: deliver (at-least-once)
    BS->>BS: look up PENDING_PAYMENT booking
    BS->>PIS: authenticate (or reuse session)
    BS->>PIS: ConfirmBooking (providerTripId, passengers)
    alt confirmation succeeds
        PIS-->>BS: providerBookingReference
        BS->>PIS: DownloadTicket
        PIS-->>BS: ticket content
        BS->>BS: persist ticket, transition to CONFIRMED
        BS->>K: publish BookingConfirmed
    else confirmation fails (docs/architecture/booking-flow.md's flagged edge case)
        BS->>BS: transition to CANCELLED (PROVIDER_CONFIRMATION_FAILED), set supportFlagged
        BS->>P: request automatic refund
        BS->>K: publish BookingCancelled
    end
```

The reservation is guaranteed active at this point under normal operation — `provider-integration-service`'s
hold TTL is deliberately longer than the maximum acceptable payment-processing time
(`docs/architecture/seat-locking-flow.md`). The `else` branch is the rare, required exception, not
the common case.

## 5. Payment Failure / Timeout

```mermaid
sequenceDiagram
    participant P as payment-service (future)
    participant K as Kafka
    participant BS as booking-service
    participant PIS as provider-integration-service

    P->>K: PaymentFailed or PaymentTimedOut
    K->>BS: deliver (at-least-once)
    BS->>BS: look up PENDING_PAYMENT booking
    BS->>PIS: ReleaseSeat (if not already expired/released)
    BS->>BS: transition to CANCELLED (PAYMENT_FAILED or PAYMENT_TIMED_OUT)
    BS->>K: publish BookingCancelled
```

No refund is requested — payment never succeeded (`docs/architecture/event-catalog.md`'s stated
failure consideration for `BookingCancelled`'s consumers).

## 6. Traveler-Initiated Cancellation (Post-Confirmation)

```mermaid
sequenceDiagram
    participant T as Traveler
    participant BS as booking-service
    participant OS as operator-service (not yet built)
    participant P as payment-service (future)
    participant K as Kafka

    T->>BS: cancel booking
    BS->>BS: look up CONFIRMED booking, verify ownership
    BS->>OS: get cancellation policy for this trip
    Note over BS,OS: operator-service does not exist yet — see boundaries.md
    OS-->>BS: fee schedule / refund eligibility
    BS->>BS: transition to CANCELLED (TRAVELER_REQUESTED)
    BS->>P: request refund (per policy)
    Note over BS: no provider-side reversal attempted — see boundaries.md's Known Gap
    BS->>K: publish BookingCancelled
    BS-->>T: cancellation confirmed
```

**No call to `provider-integration-service` appears in this diagram** — this is deliberate, not
an omission. See `boundaries.md`'s "Known Gap: Post-Confirmation Cancellation" for why.

## 7. Trip Cancellation Cascade

```mermaid
sequenceDiagram
    participant IS as inventory-service
    participant K as Kafka
    participant BS as booking-service
    participant PIS as provider-integration-service
    participant P as payment-service (future)

    IS->>K: TripCancelled
    K->>BS: deliver (at-least-once)
    loop every CONFIRMED or PENDING_PAYMENT booking for this trip
        alt PENDING_PAYMENT
            BS->>PIS: ReleaseSeat (hold)
            BS->>BS: transition to CANCELLED (TRIP_CANCELLED)
        else CONFIRMED
            BS->>BS: transition to CANCELLED (TRIP_CANCELLED)
            BS->>P: request full refund, regardless of normal policy
            Note over BS: no provider-side reversal attempted — same gap as flow 6
        end
        BS->>K: publish BookingCancelled
    end
```

The full-refund rule for the `CONFIRMED` branch is `docs/architecture/booking-flow.md` step 7's
explicit business rule — the traveler didn't cause the cancellation, so the standard per-traveler
fee schedule from `operator-service` doesn't apply, meaning this branch (unlike flow 6) needs no
`operator-service` call at all.
