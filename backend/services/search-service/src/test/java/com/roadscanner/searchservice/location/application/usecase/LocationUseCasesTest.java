package com.roadscanner.searchservice.location.application.usecase;

import com.roadscanner.searchservice.location.domain.exception.DuplicateGooglePlaceIdException;
import com.roadscanner.searchservice.location.domain.exception.LocationNotFoundException;
import com.roadscanner.searchservice.location.domain.model.GeoCoordinates;
import com.roadscanner.searchservice.location.domain.model.GooglePlaceId;
import com.roadscanner.searchservice.location.domain.model.Location;
import com.roadscanner.searchservice.location.domain.model.LocationAddress;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.port.in.CreateLocation;
import com.roadscanner.searchservice.location.domain.port.in.DisableLocation;
import com.roadscanner.searchservice.location.domain.port.in.GetLocation;
import com.roadscanner.searchservice.location.domain.port.in.SearchLocations;
import com.roadscanner.searchservice.location.domain.port.in.UpdateLocation;
import com.roadscanner.searchservice.location.testsupport.InMemoryLocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Application-layer behaviour against an in-memory repository — the orchestration rules
 * (uniqueness pre-checks, not-found translation, limit clamping, idempotent disable) without a
 * Spring context or a database.
 */
class LocationUseCasesTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");
    private static final LocationAddress HYDERABAD = new LocationAddress("Hyderabad", "Telangana", "India");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private InMemoryLocationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryLocationRepository();
    }

    private Location seedHyderabad() {
        Location location = Location.create(LocationId.generate(), "Hyderabad", HYDERABAD, null, null,
                "Asia/Kolkata", NOW);
        repository.seed(location);
        return location;
    }

    @Nested
    class Create {

        @Test
        void storesANewActiveLocationStampedWithTheClock() {
            CreateLocation useCase = new CreateLocationService(repository, clock);

            Location created = useCase.create(new CreateLocation.CreateLocationCommand(
                    "Hyderabad", HYDERABAD,
                    new GeoCoordinates(new BigDecimal("17.3850"), new BigDecimal("78.4867")),
                    new GooglePlaceId("place-1"), "Asia/Kolkata")).location();

            assertThat(created.isActive()).isTrue();
            assertThat(created.createdAt()).isEqualTo(NOW);
            assertThat(repository.count()).isEqualTo(1);
        }

        @Test
        void allowsManyLocationsWithNoGooglePlaceId() {
            CreateLocation useCase = new CreateLocationService(repository, clock);
            var command = new CreateLocation.CreateLocationCommand("Hyderabad", HYDERABAD, null, null, null);

            useCase.create(command);
            useCase.create(command);

            // Uniqueness applies only when a place id is actually present.
            assertThat(repository.count()).isEqualTo(2);
        }

        @Test
        void rejectsADuplicateGooglePlaceId() {
            CreateLocation useCase = new CreateLocationService(repository, clock);
            useCase.create(new CreateLocation.CreateLocationCommand("Hyderabad", HYDERABAD, null,
                    new GooglePlaceId("place-1"), null));

            assertThatThrownBy(() -> useCase.create(new CreateLocation.CreateLocationCommand(
                    "Hyderabad Deccan", HYDERABAD, null, new GooglePlaceId("place-1"), null)))
                    .isInstanceOf(DuplicateGooglePlaceIdException.class);
        }
    }

    @Nested
    class Get {

        @Test
        void returnsAStoredLocation() {
            Location seeded = seedHyderabad();
            GetLocation useCase = new GetLocationService(repository);

            var result = useCase.get(new GetLocation.GetLocationCommand(seeded.id()));

            assertThat(result.location().id()).isEqualTo(seeded.id());
        }

        @Test
        void stillResolvesASoftDeletedLocation() {
            Location seeded = seedHyderabad();
            seeded.disable(NOW);
            GetLocation useCase = new GetLocationService(repository);

            // Historical bookings reference withdrawn stops and must still render.
            assertThat(useCase.get(new GetLocation.GetLocationCommand(seeded.id())).location().isActive()).isFalse();
        }

        @Test
        void throwsWhenTheIdIsUnknown() {
            GetLocation useCase = new GetLocationService(repository);
            LocationId unknown = LocationId.generate();

            assertThatThrownBy(() -> useCase.get(new GetLocation.GetLocationCommand(unknown)))
                    .isInstanceOf(LocationNotFoundException.class);
        }
    }

    @Nested
    class Update {

        @Test
        void replacesFieldsAndStampsUpdatedAt() {
            Location seeded = seedHyderabad();
            UpdateLocation useCase = new UpdateLocationService(repository, clock);

            Location updated = useCase.update(new UpdateLocation.UpdateLocationCommand(
                    seeded.id(), "Hyderabad Deccan", HYDERABAD, null, null, null)).location();

            assertThat(updated.displayName()).isEqualTo("Hyderabad Deccan");
            assertThat(updated.updatedAt()).isEqualTo(NOW);
        }

        @Test
        void allowsResubmittingALocationsOwnGooglePlaceId() {
            Location seeded = Location.create(LocationId.generate(), "Hyderabad", HYDERABAD, null,
                    new GooglePlaceId("place-1"), null, NOW);
            repository.seed(seeded);
            UpdateLocation useCase = new UpdateLocationService(repository, clock);

            // Re-saving an unchanged form must not collide with the row's own place id.
            Location updated = useCase.update(new UpdateLocation.UpdateLocationCommand(
                    seeded.id(), "Hyderabad", HYDERABAD, null, new GooglePlaceId("place-1"), null)).location();

            assertThat(updated.googlePlaceId()).contains(new GooglePlaceId("place-1"));
        }

        @Test
        void rejectsAGooglePlaceIdOwnedByADifferentLocation() {
            Location owner = Location.create(LocationId.generate(), "Pune", new LocationAddress("Pune", null, "India"),
                    null, new GooglePlaceId("place-1"), null, NOW);
            Location target = seedHyderabad();
            repository.seed(owner);
            UpdateLocation useCase = new UpdateLocationService(repository, clock);

            assertThatThrownBy(() -> useCase.update(new UpdateLocation.UpdateLocationCommand(
                    target.id(), "Hyderabad", HYDERABAD, null, new GooglePlaceId("place-1"), null)))
                    .isInstanceOf(DuplicateGooglePlaceIdException.class);
        }

        @Test
        void throwsWhenTheIdIsUnknown() {
            UpdateLocation useCase = new UpdateLocationService(repository, clock);
            LocationId unknown = LocationId.generate();

            assertThatThrownBy(() -> useCase.update(new UpdateLocation.UpdateLocationCommand(
                    unknown, "Hyderabad", HYDERABAD, null, null, null)))
                    .isInstanceOf(LocationNotFoundException.class);
        }
    }

    @Nested
    class Disable {

        @Test
        void softDeletesWithoutRemovingTheRow() {
            Location seeded = seedHyderabad();
            DisableLocation useCase = new DisableLocationService(repository, clock);

            var result = useCase.disable(new DisableLocation.DisableLocationCommand(seeded.id()));

            assertThat(result.alreadyDisabled()).isFalse();
            assertThat(repository.findById(seeded.id())).isPresent();
            assertThat(repository.findById(seeded.id()).orElseThrow().isActive()).isFalse();
        }

        @Test
        void reportsARepeatedDisableHonestly() {
            Location seeded = seedHyderabad();
            DisableLocation useCase = new DisableLocationService(repository, clock);
            useCase.disable(new DisableLocation.DisableLocationCommand(seeded.id()));

            var second = useCase.disable(new DisableLocation.DisableLocationCommand(seeded.id()));

            assertThat(second.alreadyDisabled()).isTrue();
        }

        @Test
        void throwsWhenTheIdIsUnknown() {
            DisableLocation useCase = new DisableLocationService(repository, clock);
            LocationId unknown = LocationId.generate();

            assertThatThrownBy(() -> useCase.disable(new DisableLocation.DisableLocationCommand(unknown)))
                    .isInstanceOf(LocationNotFoundException.class);
        }
    }

    @Nested
    class Search {

        @BeforeEach
        void seedCatalogue() {
            repository.seed(
                    Location.create(LocationId.generate(), "Hyderabad", HYDERABAD, null, null, null, NOW),
                    Location.create(LocationId.generate(), "Hyderabad Deccan", HYDERABAD, null, null, null, NOW),
                    Location.create(LocationId.generate(), "MGBS", HYDERABAD, null, null, null, NOW),
                    Location.create(LocationId.generate(), "Pune", new LocationAddress("Pune", null, "India"),
                            null, null, null, NOW));
        }

        @Test
        void matchesOnDisplayNamePrefix() {
            SearchLocations useCase = new SearchLocationsService(repository);

            var result = useCase.search(new SearchLocations.SearchLocationsCommand("hyd", 10));

            // A partial fragment matches display names — and MGBS comes along behind them
            // because its city is Hyderabad, which is the city leg of the same query.
            assertThat(result.locations()).extracting(Location::displayName)
                    .containsExactly("Hyderabad", "Hyderabad Deccan", "MGBS");
        }

        @Test
        void alsoMatchesOnCityButRanksDisplayNameHitsFirst() {
            SearchLocations useCase = new SearchLocationsService(repository);

            var result = useCase.search(new SearchLocations.SearchLocationsCommand("Hyderabad", 10));

            assertThat(result.locations()).extracting(Location::displayName)
                    .startsWith("Hyderabad", "Hyderabad Deccan")
                    .contains("MGBS");
        }

        @Test
        void excludesSoftDeletedLocations() {
            Location withdrawn = Location.create(LocationId.generate(), "Hyderguda", HYDERABAD, null, null, null, NOW);
            withdrawn.disable(NOW);
            repository.seed(withdrawn);
            SearchLocations useCase = new SearchLocationsService(repository);

            var result = useCase.search(new SearchLocations.SearchLocationsCommand("hyderg", 10));

            assertThat(result.locations()).isEmpty();
        }

        @Test
        void clampsAnOversizedLimit() {
            SearchLocations useCase = new SearchLocationsService(repository);

            var result = useCase.search(new SearchLocations.SearchLocationsCommand("hyd", 5_000));

            assertThat(result.locations()).hasSizeLessThanOrEqualTo(SearchLocationsService.MAX_LIMIT);
        }

        @Test
        void rejectsABlankQueryRatherThanReturningTheWholeCatalogue() {
            assertThatThrownBy(() -> new SearchLocations.SearchLocationsCommand("   ", 10))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new SearchLocations.SearchLocationsCommand(null, 10))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsANonPositiveLimit() {
            assertThatThrownBy(() -> new SearchLocations.SearchLocationsCommand("hyd", 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("limit");
        }

        @Test
        void trimsTheQueryBeforeMatching() {
            SearchLocations useCase = new SearchLocationsService(repository);

            var result = useCase.search(new SearchLocations.SearchLocationsCommand("  hyd  ", 10));

            assertThat(result.locations()).isNotEmpty();
        }
    }
}
