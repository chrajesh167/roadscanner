/**
 * Redis implementation of the {@link com.roadscanner.searchservice.domain.port.out.AvailabilityCache}
 * port. See docs/architecture/high-level-design.md §7 and
 * docs/services/search-service/data-ownership.md ("The Cache-in-Front-of-a-Cache") — a derived,
 * expendable copy of a value that is itself already a live read from another service, sitting
 * in front of an already-derived index.
 */
package com.roadscanner.searchservice.adapter.out.cache;
