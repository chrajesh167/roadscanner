package com.roadscanner.searchservice.adapter.out.kafka;

import com.roadscanner.searchservice.adapter.in.event.ReviewEventListener;
import com.roadscanner.searchservice.adapter.in.event.TripEventListener;
import com.roadscanner.searchservice.domain.port.out.IndexReplayTrigger;
import org.springframework.stereotype.Component;

/**
 * Implements {@link IndexReplayTrigger} by delegating to the {@code ConsumerSeekAware}
 * capability of this service's two Kafka listener beans — see that port's Javadoc for why an
 * outbound adapter reaching into the inbound event-listener package is the pragmatic, standard
 * shape of this specific mechanic (Spring Kafka only exposes on-demand seeking through the
 * listener bean itself), not a layering mistake.
 */
@Component
class KafkaReplayTriggerAdapter implements IndexReplayTrigger {

    private final TripEventListener tripEventListener;
    private final ReviewEventListener reviewEventListener;

    KafkaReplayTriggerAdapter(TripEventListener tripEventListener, ReviewEventListener reviewEventListener) {
        this.tripEventListener = tripEventListener;
        this.reviewEventListener = reviewEventListener;
    }

    @Override
    public void triggerReplayFromBeginning() {
        tripEventListener.seekToBeginning();
        reviewEventListener.seekToBeginning();
    }
}
