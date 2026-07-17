/**
 * REST controllers and request/response DTOs — the only layer aware this is an HTTP service.
 * Organized by feature (search, detail, suggestion, admin) per
 * docs/services/search-service/api-summary.md, alongside the generic HTTP foundation:
 * global exception mapping ({@code exception}) and correlation-id propagation ({@code filter}).
 *
 * Controllers perform structural validation only (presence, range shape); domain value objects
 * re-validate their own invariants regardless — see {@code SearchController}'s Javadoc for why.
 */
package com.roadscanner.searchservice.adapter.in.rest;
