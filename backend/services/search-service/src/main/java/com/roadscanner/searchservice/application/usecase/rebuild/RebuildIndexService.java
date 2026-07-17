package com.roadscanner.searchservice.application.usecase.rebuild;

import com.roadscanner.searchservice.domain.port.in.RebuildIndex;
import com.roadscanner.searchservice.domain.port.out.IndexReplayTrigger;
import com.roadscanner.searchservice.domain.port.out.SearchableTripRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implements {@link RebuildIndex} (docs/services/search-service/use-cases.md): discard the
 * entire index, then seek every Kafka consumer back to the earliest offset so the platform's
 * retained event history repopulates it from scratch. Deliberately two separate steps against
 * two separate ports rather than one combined operation — {@link SearchableTripRepository}
 * knows nothing about Kafka, and {@link IndexReplayTrigger} knows nothing about the index's
 * storage, matching the hexagonal rule that each port owns exactly one infrastructure concern.
 *
 * Order matters: the index must be emptied *before* the replay begins, so that events landing
 * mid-rebuild are applied against a consistently empty starting point rather than racing a
 * partially-cleared one.
 */
public class RebuildIndexService implements RebuildIndex {

    private static final Logger log = LoggerFactory.getLogger(RebuildIndexService.class);

    private final SearchableTripRepository repository;
    private final IndexReplayTrigger replayTrigger;

    public RebuildIndexService(SearchableTripRepository repository, IndexReplayTrigger replayTrigger) {
        this.repository = repository;
        this.replayTrigger = replayTrigger;
    }

    @Override
    public void rebuild() {
        log.warn("Index rebuild triggered — discarding the entire search index and replaying event history from the beginning");
        repository.deleteAll();
        replayTrigger.triggerReplayFromBeginning();
        log.info("Index rebuild initiated — repopulation will proceed asynchronously as Kafka redelivers retained events");
    }
}
