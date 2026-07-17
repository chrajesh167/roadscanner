package com.roadscanner.searchservice.domain.port.out;

/**
 * Seeks every Kafka consumer this service runs back to the earliest available offset — the
 * second step of {@code RebuildIndex}, after {@link SearchableTripRepository#deleteAll()}
 * (docs/services/search-service/use-cases.md). Implemented in {@code adapter.out.kafka},
 * wrapping the {@code ConsumerSeekAware} capability of the Kafka listener beans in
 * {@code adapter.in.event} — the domain/application layer only knows "trigger a replay from the
 * beginning," never that the mechanism happens to reach into the inbound Kafka adapters to do
 * it (an implementation detail of how Spring Kafka exposes on-demand seeking).
 */
public interface IndexReplayTrigger {

    void triggerReplayFromBeginning();
}
