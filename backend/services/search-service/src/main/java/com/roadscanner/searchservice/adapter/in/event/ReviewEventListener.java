package com.roadscanner.searchservice.adapter.in.event;

import com.roadscanner.searchservice.domain.model.RatingSnapshot;
import com.roadscanner.searchservice.domain.model.TripId;
import com.roadscanner.searchservice.domain.port.in.UpdateRatingSnapshot;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consumes the review-events topic (docs/services/search-service/events-consumed.md). See
 * {@link TripEventListener}'s Javadoc for the shared error-propagation and
 * {@code ConsumerSeekAware} thread-safety rationale — identical here.
 */
@Component
public class ReviewEventListener implements ConsumerSeekAware {

    private static final Logger log = LoggerFactory.getLogger(ReviewEventListener.class);

    private final UpdateRatingSnapshot updateRatingSnapshot;
    private final Set<TopicPartition> assignedPartitions = ConcurrentHashMap.newKeySet();
    private volatile ConsumerSeekCallback seekCallback;

    public ReviewEventListener(UpdateRatingSnapshot updateRatingSnapshot) {
        this.updateRatingSnapshot = updateRatingSnapshot;
    }

    @KafkaListener(id = "review-events-listener", topics = "${roadscanner.search.kafka.review-events-topic}",
            containerFactory = "reviewEventListenerContainerFactory")
    public void onMessage(ReviewSubmittedMessage message) {
        log.debug("Received ReviewSubmitted for trip {}", message.tripId());
        updateRatingSnapshot.update(new UpdateRatingSnapshot.UpdateRatingSnapshotCommand(
                new TripId(message.tripId()),
                new RatingSnapshot(message.ratingAverage(), message.ratingReviewCount()),
                message.occurredAt()));
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
     * the review-events half of {@code RebuildIndex}. Safe to call from any thread — see this
     * class's Javadoc. */
    public void seekToBeginning() {
        ConsumerSeekCallback callback = this.seekCallback;
        if (callback == null) {
            log.warn("Replay requested before the review-events consumer registered its seek callback — skipping");
            return;
        }
        assignedPartitions.forEach(partition -> callback.seekToBeginning(partition.topic(), partition.partition()));
    }
}
