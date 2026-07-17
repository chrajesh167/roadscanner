/**
 * Use-case orchestration implementing the inbound ports declared in {@code domain.port.in},
 * coordinating domain objects and outbound ports. No HTTP, SQL, Redis, or Kafka client types
 * here — only domain and port types (plus {@code @Transactional} boundaries on the
 * event-indexing use cases; the layer is framework-light, not framework-free, matching
 * {@code auth-service}'s convention).
 *
 * Organized by feature, per docs/services/search-service/use-cases.md: {@code search},
 * {@code detail}, {@code suggestion}, {@code indexing} (the four event-driven index-maintenance
 * use cases), {@code rebuild}, and {@code availability} (a shared collaborator, not a port
 * implementation — see {@code AvailabilityOverlay}'s own Javadoc for why it lives here anyway).
 *
 * Classes carry no Spring stereotypes; they are wired explicitly in {@code config.UseCaseConfig},
 * the same discipline {@code auth-service}'s equivalent config class follows.
 */
package com.roadscanner.searchservice.application.usecase;
