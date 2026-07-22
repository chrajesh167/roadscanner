package com.roadscanner.inventoryservice.adapter.in.event;

import com.roadscanner.inventoryservice.domain.port.in.IngestRouteUpdate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes {@code operator-service}'s route-events topic — acknowledgment-only handling, see
 * {@link IngestRouteUpdate}'s Javadoc. */
@Component
class RouteEventListener {

    private final IngestRouteUpdate ingestRouteUpdate;

    RouteEventListener(IngestRouteUpdate ingestRouteUpdate) {
        this.ingestRouteUpdate = ingestRouteUpdate;
    }

    @KafkaListener(id = "route-events-listener", topics = "${roadscanner.inventory.kafka.operator-route-events-topic}",
            containerFactory = "operatorRouteEventListenerContainerFactory")
    void onMessage(OperatorRouteEventMessage message) {
        ingestRouteUpdate.ingest(new IngestRouteUpdate.Command(message.routeId(), message.origin(), message.destination()));
    }
}
