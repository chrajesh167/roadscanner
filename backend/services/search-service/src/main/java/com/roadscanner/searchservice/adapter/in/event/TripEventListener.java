package com.roadscanner.searchservice.adapter.in.event;

import com.roadscanner.searchservice.domain.model.BusType;
import com.roadscanner.searchservice.domain.model.FareSnapshot;
import com.roadscanner.searchservice.domain.model.OperatorId;
import com.roadscanner.searchservice.domain.model.Route;
import com.roadscanner.searchservice.domain.model.Schedule;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.port.in.IndexTripCancelled;
import com.roadscanner.searchservice.domain.port.in.IndexTripPublished;
import com.roadscanner.searchservice.domain.port.in.IndexTripUpdated;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Currency;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consumes the trip-events topic (docs/services/search-service/events-consumed.md), dispatching
 * on {@link TripEventMessage#eventType()} to the corresponding inbound port. Malformed input
 * (a required field missing for the event type in question) surfaces as the port command
 * record's own {@code NullPointerException}/{@code IllegalArgumentException} from its compact
 * constructor — this listener does not catch it; it propagates to the container's error handler
 * (retry-then-dead-letter, configured in {@code config.KafkaConfig}), the same
 * one-mapping-layer philosophy {@code auth-service}'s {@code GlobalExceptionHandler} applies to
 * HTTP, applied here to Kafka's equivalent failure surface.
 *
 * Implements {@link ConsumerSeekAware} solely to support {@code RebuildIndex}
 * (docs/services/search-service/use-cases.md) — {@link #seekToBeginning()} is invoked by
 * {@code adapter.out.kafka.KafkaReplayTriggerAdapter}, never by anything in this class itself,
 * typically from an HTTP request thread, not the Kafka consumer thread.
 *
 * <p>This is exactly why the callback captured in {@link #registerSeekCallback} — not the one
 * passed to {@link #onPartitionsAssigned} — is what {@link #seekToBeginning()} uses: Spring
 * Kafka's {@code ConsumerSeekCallback} is only safe to invoke from an arbitrary external thread
 * when it's the instance obtained via {@code registerSeekCallback}, which queues the seek
 * request for the consumer's own thread to apply on its next poll. Calling the
 * {@code onPartitionsAssigned} callback directly from another thread throws
 * {@code ConcurrentModificationException} ("KafkaConsumer is not safe for multi-threaded
 * access") — {@code onPartitionsAssigned} is retained here only to track which partitions are
 * currently assigned, so {@code seekToBeginning()} knows what to target.
 */
@Component
public class TripEventListener implements ConsumerSeekAware {

    private static final Logger log = LoggerFactory.getLogger(TripEventListener.class);

    private final IndexTripPublished indexTripPublished;
    private final IndexTripUpdated indexTripUpdated;
    private final IndexTripCancelled indexTripCancelled;
    private final Set<TopicPartition> assignedPartitions = ConcurrentHashMap.newKeySet();
    private volatile ConsumerSeekCallback seekCallback;

    public TripEventListener(IndexTripPublished indexTripPublished, IndexTripUpdated indexTripUpdated,
                             IndexTripCancelled indexTripCancelled) {
        this.indexTripPublished = indexTripPublished;
        this.indexTripUpdated = indexTripUpdated;
        this.indexTripCancelled = indexTripCancelled;
    }

    @KafkaListener(id = "trip-events-listener", topics = "${roadscanner.search.kafka.trip-events-topic}",
            containerFactory = "tripEventListenerContainerFactory")
    public void onMessage(TripEventMessage message) {
        log.debug("Received {} for trip {}", message.eventType(), message.tripId());
        switch (message.eventType()) {
            case PUBLISHED -> indexTripPublished.index(new IndexTripPublished.IndexTripPublishedCommand(
                    new TripId(message.tripId()),
                    new OperatorId(message.operatorId()),
                    message.operatorName(),
                    new Route(message.origin(), message.destination()),
                    new Schedule(message.departureTime(), message.arrivalTime()),
                    new BusType(message.busTypeCategory(), message.amenities()),
                    new FareSnapshot(message.fareAmount(), Currency.getInstance(message.fareCurrency())),
                    message.occurredAt()));
            case UPDATED -> indexTripUpdated.index(new IndexTripUpdated.IndexTripUpdatedCommand(
                    new TripId(message.tripId()),
                    message.operatorName(),
                    new Route(message.origin(), message.destination()),
                    new Schedule(message.departureTime(), message.arrivalTime()),
                    new BusType(message.busTypeCategory(), message.amenities()),
                    new FareSnapshot(message.fareAmount(), Currency.getInstance(message.fareCurrency())),
                    message.occurredAt()));
            case CANCELLED -> indexTripCancelled.index(new IndexTripCancelled.IndexTripCancelledCommand(
                    new TripId(message.tripId()), message.occurredAt()));
        }
    }

    @Override
    public void registerSeekCallback(ConsumerSeekCallback callback) {
        this.seekCallback = callback;
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {
        assignedPartitions.addAll(assignments.keySet());
    }

    @Override
    public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
        assignedPartitions.removeAll(partitions);
    }

    /** Seeks every partition this consumer currently holds back to the earliest offset —
     * the trip-events half of {@code RebuildIndex}. Safe to call from any thread — see this
     * class's Javadoc. */
    public void seekToBeginning() {
        ConsumerSeekCallback callback = this.seekCallback;
        if (callback == null) {
            log.warn("Replay requested before the trip-events consumer registered its seek callback — skipping");
            return;
        }
        assignedPartitions.forEach(partition -> callback.seekToBeginning(partition.topic(), partition.partition()));
    }
}
