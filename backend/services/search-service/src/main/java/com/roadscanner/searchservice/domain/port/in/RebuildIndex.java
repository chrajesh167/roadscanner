package com.roadscanner.searchservice.domain.port.in;

/**
 * Operational, not client-facing (docs/services/search-service/use-cases.md, "Rebuild Index") —
 * discards the entire index and replays the retained event history from the earliest available
 * offset, reconstructing every projection from scratch. Only possible because the index is a
 * strictly derived, disposable copy (docs/architecture/database-ownership.md); this is the
 * operational payoff of that design choice, not a hypothetical. Never triggered by a client of
 * this service's public API — see docs/services/search-service/api-summary.md.
 */
public interface RebuildIndex {

    void rebuild();
}
