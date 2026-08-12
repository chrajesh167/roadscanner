package com.roadscanner.bookingservice.adapter.out.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * The wire shape published to {@code booking-events} — single topic, discriminated by
 * {@code eventType}, keyed by {@code bookingId}, matching the single-topic-per-domain convention
 * {@code inventory-service}'s {@code CatalogTripEventMessage} and
 * {@code provider-integration-service}'s {@code ProviderAuditMessage} already establish
 * (docs/services/booking-service/events-published.md). {@code cancellationReason} is {@code null}
 * unless {@code eventType = CANCELLED}.
 *
 * <h2>Why this carries contact details</h2>
 * {@code notification-service} is a documented consumer of {@code BookingConfirmed} and
 * {@code BookingCancelled} (events-published.md), and a notification is worthless without a
 * recipient. That recipient exists only here — {@code Contact} is part of the {@code Booking}
 * aggregate and no other service holds it, so a consumer that had to fetch it would turn an
 * asynchronous fan-out into a synchronous dependency on this service being up.
 *
 * <p>Everything added is read straight off the already-loaded {@code Booking}: no lookup, no
 * enrichment call, nothing computed. Origin and destination are deliberately <strong>absent</strong>
 * — this service genuinely does not know them (they live in the catalog), and fetching them here
 * would put an inventory call on the publish path of a booking that is already durable.
 *
 * <p>The payload schema is this service's own decision to make: events-published.md fixes only the
 * minimum ({@code eventType, bookingId, travelerId, tripId, status, occurredAt}, plus
 * {@code cancellationReason} on cancellation) and states the exact wire schema is not fixed there.
 * Existing consumers deserialize with {@code FAIL_ON_UNKNOWN_PROPERTIES} disabled, so the added
 * fields are ignored by anything that does not want them.
 *
 * <p>Deliberately <em>not</em> carried: passenger names and dates of birth. A notification greets
 * the contact and names the booking; it never needs the manifest, and a topic several services read
 * is the wrong place to broadcast it.
 *
 * @param eventId a fresh identifier per publish, so an at-least-once redelivery is recognisable as
 *                the same event. Consumers key their idempotency on it — without one, a redelivered
 *                confirmation is indistinguishable from a second confirmation.
 */
public record BookingEventMessage(BookingEventType eventType, UUID bookingId, UUID travelerId, UUID tripId,
                                   String status, String cancellationReason, Instant occurredAt,
                                   UUID eventId, String bookingReference, String contactEmail,
                                   String contactPhone, String communicationPreference,
                                   Instant departureTime, BigDecimal fareAmount, String fareCurrency) {
}
