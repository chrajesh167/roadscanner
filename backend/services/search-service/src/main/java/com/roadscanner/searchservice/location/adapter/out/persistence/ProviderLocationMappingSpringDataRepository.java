package com.roadscanner.searchservice.location.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Raw Spring Data access for provider mappings. Every finder here is backed by one of V2's
 * composite indexes ({@code idx_provider_city}, {@code idx_provider_station},
 * {@code idx_provider_location}).
 */
interface ProviderLocationMappingSpringDataRepository
        extends JpaRepository<ProviderLocationMappingJpaEntity, UUID> {

    Optional<ProviderLocationMappingJpaEntity> findByLocationIdAndProvider(UUID locationId, String provider);

    List<ProviderLocationMappingJpaEntity> findByLocationId(UUID locationId);

    Optional<ProviderLocationMappingJpaEntity> findByProviderAndProviderCityId(String provider, String providerCityId);

    Optional<ProviderLocationMappingJpaEntity> findByProviderAndProviderStationId(String provider,
                                                                                 String providerStationId);
}
