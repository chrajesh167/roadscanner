package com.roadscanner.bookingservice.adapter.in.event;

import java.time.Instant;

/**
 * The wire shape of a message on {@code provider-integration-service}'s audit topic
 * (single topic, discriminated by {@code eventType}, keyed by {@code providerType} — that
 * service's own {@code events-published.md}). <strong>{@code SeatReleased} is specified but not
 * yet published by that service's current implementation</strong>
 * (docs/services/provider-integration-service/events-published.md) — this record is therefore
 * this service's own, reasonable specification of the fields {@code Handle Seat Released}
 * (docs/services/booking-service/use-cases.md) needs, not a copy of an already-shipped shape.
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} is disabled platform-wide ({@code config.JacksonConfig}), so
 * this type tolerates whatever additional fields the real {@code ProviderUnavailable}/
 * {@code ProviderRecovered}/{@code SessionExpired} messages already flowing on this topic carry —
 * this service ignores every {@code eventType} except {@code "SeatReleased"}. Reconcile this
 * record against the real shape the moment {@code provider-integration-service} implements the
 * event.
 */
public record ProviderAuditMessage(String eventType, String providerType, String providerBlockReference,
                                    Instant occurredAt) {
}
