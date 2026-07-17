package com.roadscanner.searchservice.adapter.in.rest.admin;

import com.roadscanner.searchservice.domain.port.in.RebuildIndex;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Operational index rebuild (docs/services/search-service/use-cases.md, "Rebuild Index") —
 * deliberately under {@code /internal}, not {@code /api/v1}, to signal it is not part of this
 * service's client-facing contract (docs/services/search-service/api-summary.md never lists
 * it). Per this project's explicit scope for this service, no authentication/authorization is
 * implemented here at all — this endpoint's protection is expected to come from
 * {@code api-gateway} routing rules never exposing {@code /internal/**} publicly, or from
 * network-level restriction to internal callers only. See this service's README "Remaining
 * Integration Points" for the operational follow-up this implies.
 */
@RestController
@RequestMapping("/internal/search")
@Tag(name = "Reindex", description = "Operational index rebuild — not part of the public API")
class ReindexController {

    private final RebuildIndex rebuildIndex;

    ReindexController(RebuildIndex rebuildIndex) {
        this.rebuildIndex = rebuildIndex;
    }

    @PostMapping("/reindex")
    @Operation(summary = "Rebuild the search index",
            description = "Discards the entire index and replays retained Kafka history to reconstruct it. "
                    + "Asynchronous — returns immediately, repopulation proceeds in the background.")
    @ApiResponse(responseCode = "202", description = "Rebuild initiated")
    ResponseEntity<Void> reindex() {
        rebuildIndex.rebuild();
        return ResponseEntity.accepted().build();
    }
}
