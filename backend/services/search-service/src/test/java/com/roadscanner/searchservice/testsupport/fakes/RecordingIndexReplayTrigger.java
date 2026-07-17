package com.roadscanner.searchservice.testsupport.fakes;

import com.roadscanner.searchservice.domain.port.out.IndexReplayTrigger;

/** Records whether/when a replay was triggered, for assertions. */
public final class RecordingIndexReplayTrigger implements IndexReplayTrigger {

    private int triggerCount;

    @Override
    public void triggerReplayFromBeginning() {
        triggerCount++;
    }

    public int triggerCount() {
        return triggerCount;
    }
}
