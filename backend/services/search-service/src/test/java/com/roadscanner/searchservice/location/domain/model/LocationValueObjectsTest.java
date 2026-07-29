package com.roadscanner.searchservice.location.domain.model;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Value objects must never be constructible into an invalid state, whoever the caller is. */
class LocationValueObjectsTest {

    @Nested
    class Coordinates {

        @Test
        void rejectsAnOutOfRangeLatitude() {
            assertThatThrownBy(() -> new GeoCoordinates(new BigDecimal("91"), BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("latitude");
        }

        @Test
        void rejectsAnOutOfRangeLongitude() {
            assertThatThrownBy(() -> new GeoCoordinates(BigDecimal.ZERO, new BigDecimal("-181")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("longitude");
        }

        @Test
        void normalisesScaleSoEqualityMatchesTheDatabase() {
            GeoCoordinates one = new GeoCoordinates(new BigDecimal("17.385"), new BigDecimal("78.4867"));
            GeoCoordinates two = new GeoCoordinates(new BigDecimal("17.3850000"), new BigDecimal("78.4867000"));

            assertThat(one).isEqualTo(two);
        }

        @Test
        void ofNullableAcceptsBothAbsent() {
            assertThat(GeoCoordinates.ofNullable(null, null)).isNull();
        }

        @Test
        void ofNullableRejectsAHalfSuppliedPair() {
            assertThatThrownBy(() -> GeoCoordinates.ofNullable(new BigDecimal("17.385"), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("together");
        }
    }

    @Nested
    class Address {

        @Test
        void requiresCityAndCountry() {
            assertThatThrownBy(() -> new LocationAddress("  ", null, "India"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("city");

            assertThatThrownBy(() -> new LocationAddress("Hyderabad", null, "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("country");
        }

        @Test
        void normalisesABlankStateToAbsent() {
            LocationAddress address = new LocationAddress("Hyderabad", "   ", "India");

            assertThat(address.state()).isNull();
            assertThat(address.stateIfPresent()).isEmpty();
        }

        @Test
        void rejectsANullCityOrCountry() {
            assertThatThrownBy(() -> new LocationAddress(null, null, "India"))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("city");

            assertThatThrownBy(() -> new LocationAddress("Hyderabad", null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("country");
        }

        @Test
        void trimsSurroundingWhitespace() {
            LocationAddress address = new LocationAddress("  Hyderabad  ", "  Telangana  ", "  India  ");

            assertThat(address.city()).isEqualTo("Hyderabad");
            assertThat(address.state()).isEqualTo("Telangana");
            assertThat(address.country()).isEqualTo("India");
        }

        @Test
        void rendersItselfUnambiguouslyWithAndWithoutAState() {
            assertThat(new LocationAddress("Hyderabad", "Telangana", "India"))
                    .hasToString("Hyderabad, Telangana, India");
            assertThat(new LocationAddress("Singapore", null, "Singapore"))
                    .hasToString("Singapore, Singapore");
        }
    }

    @Nested
    class GooglePlace {

        @Test
        void treatsNullAndBlankAsNoPlaceKnown() {
            assertThat(GooglePlaceId.ofNullable(null)).isNull();
            assertThat(GooglePlaceId.ofNullable("   ")).isNull();
        }

        @Test
        void rejectsAnOverlongValue() {
            assertThatThrownBy(() -> new GooglePlaceId("x".repeat(256)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void acceptsTheLongestPermittedValue() {
            assertThat(new GooglePlaceId("x".repeat(255)).value()).hasSize(255);
        }

        @Test
        void rejectsNullAndBlankWhenConstructedDirectly() {
            assertThatThrownBy(() -> new GooglePlaceId(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new GooglePlaceId("   ")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void trimsAndRendersItsRawValue() {
            assertThat(GooglePlaceId.ofNullable("  place-hyd  ")).hasToString("place-hyd");
        }
    }

    @Nested
    class Provider {

        @Test
        void normalisesCaseSoOneProviderCannotBecomeTwo() {
            assertThat(new ProviderCode("flixbus")).isEqualTo(new ProviderCode("FlixBus"));
            assertThat(new ProviderCode("flixbus").value()).isEqualTo("FLIXBUS");
        }

        @Test
        void rejectsBlank() {
            assertThatThrownBy(() -> new ProviderCode(" "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsNullAndOverlongValues() {
            assertThatThrownBy(() -> new ProviderCode(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new ProviderCode("x".repeat(51)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("50");
        }

        @Test
        void rendersItsNormalisedValue() {
            assertThat(new ProviderCode("flixbus")).hasToString("FLIXBUS");
        }
    }

    @Nested
    class PlaceRef {

        @Test
        void requiresAtLeastOneProviderIdentifier() {
            assertThatThrownBy(() -> new ProviderPlaceRef(null, null, "MGBS"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least");
        }

        @Test
        void acceptsACityOnlyMapping() {
            ProviderPlaceRef ref = new ProviderPlaceRef("58291", null, null);

            assertThat(ref.cityId()).isEqualTo("58291");
            assertThat(ref.identifiesStation()).isFalse();
        }

        @Test
        void normalisesBlanksToAbsent() {
            ProviderPlaceRef ref = new ProviderPlaceRef("58291", "   ", "  ");

            assertThat(ref.stationId()).isNull();
            assertThat(ref.stationName()).isNull();
        }

        @Test
        void acceptsAStationOnlyMapping() {
            ProviderPlaceRef ref = new ProviderPlaceRef(null, "station-1", "MGBS");

            // Providers model geography inconsistently — some expose only stations.
            assertThat(ref.identifiesStation()).isTrue();
            assertThat(ref.cityId()).isNull();
        }

        @Test
        void rejectsAnOverlongIdentifier() {
            assertThatThrownBy(() -> new ProviderPlaceRef("x".repeat(256), null, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("providerCityId");

            assertThatThrownBy(() -> new ProviderPlaceRef("58291", "x".repeat(256), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("providerStationId");

            assertThatThrownBy(() -> new ProviderPlaceRef("58291", null, "x".repeat(256)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("providerStationName");
        }
    }

    @Nested
    class Identity {

        @Test
        void locationIdsAreMintedUniquely() {
            assertThat(LocationId.generate()).isNotEqualTo(LocationId.generate());
        }

        @Test
        void locationIdParsesAndRendersItsCanonicalForm() {
            LocationId id = LocationId.generate();

            assertThat(LocationId.of(id.toString())).isEqualTo(id);
            assertThat(id).hasToString(id.value().toString());
        }

        @Test
        void locationIdRejectsNullAndMalformedValues() {
            assertThatThrownBy(() -> new LocationId(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> LocationId.of(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> LocationId.of("not-a-uuid")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void mappingIdsAreMintedUniquelyAndRenderTheirValue() {
            ProviderLocationMappingId id = ProviderLocationMappingId.generate();

            assertThat(id).isNotEqualTo(ProviderLocationMappingId.generate());
            assertThat(id).hasToString(id.value().toString());
            assertThatThrownBy(() -> new ProviderLocationMappingId(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void coordinatesRenderAsALatLongPair() {
            assertThat(new GeoCoordinates(new BigDecimal("17.385"), new BigDecimal("78.4867")))
                    .hasToString("17.3850000,78.4867000");
        }

        @Test
        void coordinatesRejectNulls() {
            assertThatThrownBy(() -> new GeoCoordinates(null, BigDecimal.ZERO))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new GeoCoordinates(BigDecimal.ZERO, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
