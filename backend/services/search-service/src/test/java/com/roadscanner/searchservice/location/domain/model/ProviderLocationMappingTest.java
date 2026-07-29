package com.roadscanner.searchservice.location.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderLocationMappingTest {

    private static final Instant CREATED = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant SYNCED = Instant.parse("2026-07-05T00:00:00Z");
    private static final ProviderCode FLIXBUS = new ProviderCode("FLIXBUS");
    private static final ProviderPlaceRef MGBS = new ProviderPlaceRef("58291", "station-1", "MGBS");

    private static ProviderLocationMapping mapping() {
        return ProviderLocationMapping.create(ProviderLocationMappingId.generate(), FLIXBUS, LocationId.generate(),
                MGBS, "{\"platform\":\"3\"}", CREATED);
    }

    @Test
    void startsUnverifiedAndUnsynced() {
        ProviderLocationMapping mapping = mapping();

        assertThat(mapping.isVerified()).isFalse();
        assertThat(mapping.lastSynced()).isEmpty();
    }

    @Test
    void markVerifiedIsIdempotent() {
        ProviderLocationMapping mapping = mapping();

        assertThat(mapping.markVerified(SYNCED)).isTrue();
        assertThat(mapping.markVerified(SYNCED)).isFalse();
        assertThat(mapping.isVerified()).isTrue();
    }

    @Test
    void resyncWithUnchangedIdentifiersKeepsVerification() {
        ProviderLocationMapping mapping = mapping();
        mapping.markVerified(CREATED);

        mapping.recordSync(new ProviderPlaceRef("58291", "station-1", "MGBS"), "{}", SYNCED);

        // Routine polling must not churn a human confirmation.
        assertThat(mapping.isVerified()).isTrue();
        assertThat(mapping.lastSynced()).contains(SYNCED);
    }

    @Test
    void resyncWithChangedIdentifiersInvalidatesVerification() {
        ProviderLocationMapping mapping = mapping();
        mapping.markVerified(CREATED);

        mapping.recordSync(new ProviderPlaceRef("58291", "station-9", "MGBS Bay 2"), "{}", SYNCED);

        // The previous confirmation applied to the old identifiers and cannot carry over.
        assertThat(mapping.isVerified()).isFalse();
        assertThat(mapping.placeRef().stationId()).isEqualTo("station-9");
    }

    @Test
    void metadataIsCarriedOpaquely() {
        ProviderLocationMapping mapping = mapping();

        assertThat(mapping.metadataJson()).contains("{\"platform\":\"3\"}");
    }

    @Test
    void metadataIsOptional() {
        ProviderLocationMapping mapping = ProviderLocationMapping.create(ProviderLocationMappingId.generate(),
                FLIXBUS, LocationId.generate(), MGBS, null, CREATED);

        assertThat(mapping.metadataJson()).isEmpty();
    }

    @Test
    void exposesItsOwnIdentityAndTimestamps() {
        ProviderLocationMappingId id = ProviderLocationMappingId.generate();
        LocationId locationId = LocationId.generate();

        ProviderLocationMapping mapping = ProviderLocationMapping.create(id, FLIXBUS, locationId, MGBS, null, CREATED);

        assertThat(mapping.id()).isEqualTo(id);
        assertThat(mapping.provider()).isEqualTo(FLIXBUS);
        assertThat(mapping.locationId()).isEqualTo(locationId);
        assertThat(mapping.placeRef()).isEqualTo(MGBS);
        assertThat(mapping.createdAt()).isEqualTo(CREATED);
        assertThat(mapping.updatedAt()).isEqualTo(CREATED);
    }

    @Test
    void reconstituteRestoresPersistedStateVerbatim() {
        ProviderLocationMapping mapping = ProviderLocationMapping.reconstitute(ProviderLocationMappingId.generate(),
                FLIXBUS, LocationId.generate(), MGBS, "{}", true, SYNCED, CREATED, SYNCED);

        // Rehydration trusts the stored state rather than re-running create()'s defaults —
        // otherwise a verified, previously-synced row would come back looking brand new.
        assertThat(mapping.isVerified()).isTrue();
        assertThat(mapping.lastSynced()).contains(SYNCED);
        assertThat(mapping.updatedAt()).isEqualTo(SYNCED);
    }

    @Test
    void identityIsTheIdAloneRegardlessOfFieldDrift() {
        ProviderLocationMappingId id = ProviderLocationMappingId.generate();
        ProviderLocationMapping one = ProviderLocationMapping.create(id, FLIXBUS, LocationId.generate(), MGBS,
                null, CREATED);
        ProviderLocationMapping same = ProviderLocationMapping.create(id, new ProviderCode("REDBUS"),
                LocationId.generate(), new ProviderPlaceRef("HYD", null, null), "{}", SYNCED);

        assertThat(one).isEqualTo(one).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(one).isNotEqualTo(mapping()).isNotEqualTo("not a mapping");
    }

    @Test
    void rendersTheProviderAndLocationItBridges() {
        LocationId locationId = LocationId.generate();
        ProviderLocationMapping mapping = ProviderLocationMapping.create(ProviderLocationMappingId.generate(),
                FLIXBUS, locationId, MGBS, null, CREATED);

        assertThat(mapping).hasToString("ProviderLocationMapping[FLIXBUS -> " + locationId + "]");
    }
}
