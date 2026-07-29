package com.roadscanner.searchservice.location.adapter.out.persistence;

import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMappingId;
import com.roadscanner.searchservice.location.domain.model.ProviderPlaceRef;

/** Single bridge between {@link ProviderLocationMapping} and its persistence shape. Stateless. */
final class ProviderLocationMappingMapper {

    ProviderLocationMapping toDomain(ProviderLocationMappingJpaEntity entity) {
        return ProviderLocationMapping.reconstitute(
                new ProviderLocationMappingId(entity.getId()),
                new ProviderCode(entity.getProvider()),
                new LocationId(entity.getLocationId()),
                new ProviderPlaceRef(entity.getProviderCityId(), entity.getProviderStationId(),
                        entity.getProviderStationName()),
                entity.getProviderMetadata(),
                entity.isVerified(),
                entity.getLastSynced(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    ProviderLocationMappingJpaEntity toEntity(ProviderLocationMapping mapping) {
        ProviderPlaceRef ref = mapping.placeRef();
        return new ProviderLocationMappingJpaEntity(
                mapping.id().value(),
                mapping.provider().value(),
                mapping.locationId().value(),
                ref.cityId(),
                ref.stationId(),
                ref.stationName(),
                mapping.metadataJson().orElse(null),
                mapping.isVerified(),
                mapping.lastSynced().orElse(null),
                mapping.createdAt(),
                mapping.updatedAt());
    }

    void applyTo(ProviderLocationMappingJpaEntity entity, ProviderLocationMapping mapping) {
        ProviderPlaceRef ref = mapping.placeRef();
        entity.setProviderCityId(ref.cityId());
        entity.setProviderStationId(ref.stationId());
        entity.setProviderStationName(ref.stationName());
        entity.setProviderMetadata(mapping.metadataJson().orElse(null));
        entity.setVerified(mapping.isVerified());
        entity.setLastSynced(mapping.lastSynced().orElse(null));
        entity.setUpdatedAt(mapping.updatedAt());
    }
}
