package com.roadscanner.inventoryservice.adapter.in.event;

import com.roadscanner.inventoryservice.domain.port.in.IngestOperatorUpdate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes {@code operator-service}'s operator-events topic. */
@Component
class OperatorEventListener {

    private final IngestOperatorUpdate ingestOperatorUpdate;

    OperatorEventListener(IngestOperatorUpdate ingestOperatorUpdate) {
        this.ingestOperatorUpdate = ingestOperatorUpdate;
    }

    @KafkaListener(id = "operator-events-listener", topics = "${roadscanner.inventory.kafka.operator-operator-events-topic}",
            containerFactory = "operatorOperatorEventListenerContainerFactory")
    void onMessage(OperatorOperatorEventMessage message) {
        ingestOperatorUpdate.ingest(new IngestOperatorUpdate.Command(message.operatorId(), message.displayName()));
    }
}
