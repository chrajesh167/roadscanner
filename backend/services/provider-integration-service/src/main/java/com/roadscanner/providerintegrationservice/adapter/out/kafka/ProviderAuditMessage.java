package com.roadscanner.providerintegrationservice.adapter.out.kafka;

import java.time.Instant;

/**
 * The wire shape published to the {@code provider-integration-events} topic — deliberately a
 * separate type from the domain's {@code AuditRecord}, so the domain model can evolve without
 * silently changing this service's public event contract (docs/services/provider-integration-service/events-published.md).
 * A single topic carrying all three event types, discriminated by {@code eventType}, matching
 * {@code search-service}'s {@code TripEventMessage}/{@code TripEventType} precedent.
 */
public record ProviderAuditMessage(String eventType, String providerType, String sessionId, String message,
                                    Instant occurredAt) {
}
