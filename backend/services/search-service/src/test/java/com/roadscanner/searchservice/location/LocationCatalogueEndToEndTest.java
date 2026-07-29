package com.roadscanner.searchservice.location;

import com.roadscanner.searchservice.adapter.out.security.EphemeralJwtKeyPair;
import com.roadscanner.searchservice.location.domain.model.LocationId;
import com.roadscanner.searchservice.location.domain.model.ProviderCode;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMapping;
import com.roadscanner.searchservice.location.domain.model.ProviderLocationMappingId;
import com.roadscanner.searchservice.location.domain.model.ProviderPlaceRef;
import com.roadscanner.searchservice.location.domain.port.in.GetProviderMapping;
import com.roadscanner.searchservice.location.domain.port.out.ProviderLocationMappingRepository;
import com.roadscanner.searchservice.testsupport.TestcontainersConfiguration;
import com.roadscanner.searchservice.testsupport.security.TestJwtIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full HTTP-surface flows for the location catalogue against real Postgres, Redis, and Kafka
 * (Testcontainers) — the same shape as {@code SearchServiceEndToEndTest}, proving the REST
 * adapter, use cases, JPA adapter, and the V2 migration work together rather than each in
 * isolation.
 *
 * <p>Also asserts, at the seam Sprint 3 will build on, that provider identifiers resolve
 * <em>in-process only</em>: {@link GetProviderMapping} answers, while nothing on the HTTP surface
 * ever emits a provider id.
 *
 * <p>No {@code @Transactional} here on purpose — a rollback would hide exactly the commit-time
 * behaviour (constraints, generated defaults) this test exists to prove. Each test therefore uses
 * its own randomised display name rather than relying on a clean table.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class LocationCatalogueEndToEndTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private ProviderLocationMappingRepository mappingRepository;

    @Autowired
    private GetProviderMapping getProviderMapping;

    /** Present only because the test profile sets ephemeral-keys — see config.JwtConfig. */
    @Autowired
    private EphemeralJwtKeyPair jwtKeyPair;

    @Value("${roadscanner.security.jwt.issuer}")
    private String issuer;

    private TestJwtIssuer tokens;

    @BeforeEach
    void mintIssuer() {
        tokens = new TestJwtIssuer(jwtKeyPair, issuer);
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private Map<String, Object> request(String displayName, String city) {
        Map<String, Object> body = new HashMap<>();
        body.put("displayName", displayName);
        body.put("city", city);
        body.put("state", "Telangana");
        body.put("country", "India");
        body.put("timezone", "Asia/Kolkata");
        return body;
    }

    /** Write requests carry a genuinely signed ADMIN token — the real decoder verifies it. */
    private HttpEntity<Map<String, Object>> entity(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokens.issueAdmin());
        return new HttpEntity<>(body, headers);
    }

    private HttpEntity<Void> adminRequest() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.issueAdmin());
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Map<String, Object>> entityWithRole(Map<String, Object> body, String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokens.issue(UUID.randomUUID(), role));
        return new HttpEntity<>(body, headers);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody();
    }

    @SuppressWarnings("rawtypes")
    private Map<String, Object> create(Map<String, Object> request) {
        ResponseEntity<Map> response = rest.postForEntity("/api/v1/locations", entity(request), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return body(response);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private List<Map<String, Object>> autocomplete(String query) {
        ResponseEntity<Map> response = rest.getForEntity("/api/v1/locations?q={q}", Map.class, query);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (List<Map<String, Object>>) body(response).get("suggestions");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void aCreatedLocationIsResolvableByItsRoadScannerIdAndAppearsInAutocomplete() {
        String displayName = unique("Hyderabad");
        Map<String, Object> created = create(request(displayName, displayName));

        String id = (String) created.get("id");
        assertThat(id).isNotNull();
        assertThat(created.get("active")).isEqualTo(true);
        assertThat(created.get("createdAt")).isNotNull();

        ResponseEntity<Map> fetched = rest.getForEntity("/api/v1/locations/{id}", Map.class, id);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(fetched).get("displayName")).isEqualTo(displayName);
        assertThat(body(fetched).get("country")).isEqualTo("India");
        assertThat(body(fetched).get("timezone")).isEqualTo("Asia/Kolkata");

        assertThat(autocomplete(displayName)).extracting(row -> row.get("id")).contains(id);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void coordinatesAndGooglePlaceIdSurviveARoundTrip() {
        String displayName = unique("Hyderabad");
        Map<String, Object> request = request(displayName, displayName);
        request.put("latitude", "17.3850000");
        request.put("longitude", "78.4867000");
        request.put("googlePlaceId", unique("place"));

        String id = (String) create(request).get("id");

        ResponseEntity<Map> fetched = rest.getForEntity("/api/v1/locations/{id}", Map.class, id);
        assertThat(body(fetched).get("latitude").toString()).startsWith("17.385");
        assertThat(body(fetched).get("longitude").toString()).startsWith("78.4867");
        assertThat(body(fetched).get("googlePlaceId")).isEqualTo(request.get("googlePlaceId"));
    }

    @Test
    void aDuplicateGooglePlaceIdIsRejectedWithAConflict() {
        String placeId = unique("place");
        Map<String, Object> first = request(unique("Hyderabad"), "Hyderabad");
        first.put("googlePlaceId", placeId);
        create(first);

        Map<String, Object> second = request(unique("Hyderabad Deccan"), "Hyderabad");
        second.put("googlePlaceId", placeId);

        ResponseEntity<String> response = rest.postForEntity("/api/v1/locations", entity(second), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void putReplacesTheEntryAndClearsOmittedOptionalFields() {
        Map<String, Object> request = request(unique("Hyderabad"), "Hyderabad");
        request.put("latitude", "17.3850000");
        request.put("longitude", "78.4867000");
        String id = (String) create(request).get("id");

        String renamed = unique("Hyderabad Deccan");
        Map<String, Object> replacement = new HashMap<>();
        replacement.put("displayName", renamed);
        replacement.put("city", "Hyderabad");
        replacement.put("country", "India");

        ResponseEntity<Map> response = rest.exchange("/api/v1/locations/{id}", HttpMethod.PUT,
                entity(replacement), Map.class, id);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(response).get("displayName")).isEqualTo(renamed);
        // Full replace, not a patch: the omitted coordinates and timezone are cleared.
        assertThat(body(response).get("latitude")).isNull();
        assertThat(body(response).get("state")).isNull();
        assertThat(body(response).get("timezone")).isNull();
    }

    @Test
    @SuppressWarnings("rawtypes")
    void deleteIsASoftDeleteThatWithdrawsFromAutocompleteButStaysResolvable() {
        String displayName = unique("Hyderguda");
        String id = (String) create(request(displayName, displayName)).get("id");
        assertThat(autocomplete(displayName)).hasSize(1);

        ResponseEntity<Void> deleted = rest.exchange("/api/v1/locations/{id}", HttpMethod.DELETE,
                adminRequest(), Void.class, id);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Withdrawn from suggestions…
        assertThat(autocomplete(displayName)).isEmpty();

        // …but still resolvable, because historical bookings reference it.
        ResponseEntity<Map> fetched = rest.getForEntity("/api/v1/locations/{id}", Map.class, id);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(body(fetched).get("active")).isEqualTo(false);
    }

    @Test
    void repeatedDeletesSucceed() {
        String id = (String) create(request(unique("Hyderguda"), "Hyderabad")).get("id");

        for (int attempt = 0; attempt < 2; attempt++) {
            ResponseEntity<Void> response = rest.exchange("/api/v1/locations/{id}", HttpMethod.DELETE,
                    adminRequest(), Void.class, id);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }

    @Test
    void anUnknownIdIsANotFoundOnEveryRoute() {
        UUID unknown = UUID.randomUUID();

        assertThat(rest.getForEntity("/api/v1/locations/{id}", String.class, unknown).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.exchange("/api/v1/locations/{id}", HttpMethod.PUT,
                entity(request("Hyderabad", "Hyderabad")), String.class, unknown).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.exchange("/api/v1/locations/{id}", HttpMethod.DELETE,
                adminRequest(), String.class, unknown).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void invalidInputIsRejectedBeforeAnythingIsStored() {
        Map<String, Object> missingCountry = new HashMap<>();
        missingCountry.put("displayName", "Hyderabad");
        missingCountry.put("city", "Hyderabad");

        assertThat(rest.postForEntity("/api/v1/locations", entity(missingCountry), String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(rest.getForEntity("/api/v1/locations?q=  ", String.class).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void autocompleteRanksDisplayNameHitsAboveCityOnlyHits() {
        String city = unique("Hyderabad");
        create(request(unique("MGBS"), city));
        create(request(city, city));

        List<Map<String, Object>> suggestions = autocomplete(city);

        assertThat(suggestions).hasSize(2);
        assertThat(suggestions.getFirst().get("displayName")).isEqualTo(city);
    }

    // ---------- Authorization, over real HTTP with genuinely signed tokens ----------
    //
    // Unlike LocationControllerTest, nothing is stubbed here: these drive the real NimbusJwtDecoder
    // (signature, issuer, expiry) and SecurityConfig's role-claim→authority converter. That is what
    // makes them the actual proof that "role": "ADMIN" in an auth-service token grants the write.

    @Test
    void anAdminTokenIsAcceptedForEveryWriteRoute() {
        String id = (String) create(request(unique("Hyderabad"), "Hyderabad")).get("id");

        assertThat(rest.exchange("/api/v1/locations/{id}", HttpMethod.PUT,
                entity(request(unique("Hyderabad Deccan"), "Hyderabad")), String.class, id).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(rest.exchange("/api/v1/locations/{id}", HttpMethod.DELETE,
                adminRequest(), String.class, id).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void aNonAdminTokenIsForbiddenOnEveryWriteRoute() {
        String id = (String) create(request(unique("Hyderabad"), "Hyderabad")).get("id");
        HttpEntity<Map<String, Object>> traveler =
                entityWithRole(request(unique("Hyderabad"), "Hyderabad"), "TRAVELER");

        assertThat(rest.postForEntity("/api/v1/locations", traveler, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.exchange("/api/v1/locations/{id}", HttpMethod.PUT, traveler, String.class, id)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.exchange("/api/v1/locations/{id}", HttpMethod.DELETE, traveler, String.class, id)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Rejected, not merely unauthorised-then-ignored: the entry must survive untouched.
        assertThat(body(rest.getForEntity("/api/v1/locations/{id}", Map.class, id)).get("active")).isEqualTo(true);
    }

    @Test
    void anUnauthenticatedWriteIsRejected() {
        HttpHeaders jsonOnly = new HttpHeaders();
        jsonOnly.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> anonymous =
                new HttpEntity<>(request(unique("Hyderabad"), "Hyderabad"), jsonOnly);

        assertThat(rest.postForEntity("/api/v1/locations", anonymous, String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void everyPublicReadRouteStaysAnonymous() {
        String displayName = unique("Hyderabad");
        String id = (String) create(request(displayName, displayName)).get("id");

        // No Authorization header on any of these — adding security must not have cost the public
        // read surface its anonymous access, on the location routes or the pre-existing search ones.
        assertThat(rest.getForEntity("/api/v1/locations/{id}", String.class, id).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/api/v1/locations?q={q}", String.class, displayName).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity(
                "/api/v1/search/trips?origin=Mumbai&destination=Pune&date=2026-08-01", String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/api/v1/search/suggestions?query=Mum", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(rest.getForEntity("/v3/api-docs", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void everyLocationEndpointIsPublishedInTheOpenApiSpec() {
        String spec = rest.getForObject("/v3/api-docs", String.class);

        // ARCHITECTURE_RULES.md: every service must expose OpenAPI. A route that is not in the
        // published spec is, for an API consumer, a route that does not exist.
        assertThat(spec).contains("\"/api/v1/locations\"", "\"/api/v1/locations/{id}\"")
                .contains("Autocomplete locations", "Get a location", "Create a location (admin)",
                        "Replace a location (admin)", "Withdraw a location (admin)")
                .contains("LocationRequest", "LocationResponse", "LocationSummary", "AutocompleteResponse");

        // The provider-mapping port is deliberately in-process: no route, and no provider field
        // on any published schema. (The word "provider" does appear in prose — DELETE explains
        // that provider mappings reference the row — so assert on the identifiers, not the text.)
        assertThat(spec).doesNotContain("/api/v1/providers", "ProviderLocationMapping",
                "providerCityId", "providerStationId", "providerStationName");
    }

    @Test
    void providerIdentifiersResolveInProcessAndNeverOverHttp() {
        String displayName = unique("Hyderabad");
        String id = (String) create(request(displayName, displayName)).get("id");
        LocationId locationId = LocationId.of(id);
        ProviderCode flixbus = new ProviderCode("FLIXBUS");

        mappingRepository.save(ProviderLocationMapping.create(ProviderLocationMappingId.generate(), flixbus,
                locationId, new ProviderPlaceRef("58291", "station-1", "MGBS"),
                "{\"platform\":\"7\"}", Instant.now()));

        // An integration-boundary caller can translate a RoadScanner id into provider ids…
        ProviderLocationMapping mapping = getProviderMapping
                .get(new GetProviderMapping.GetProviderMappingCommand(locationId, flixbus))
                .mapping()
                .orElseThrow();
        assertThat(mapping.placeRef().cityId()).isEqualTo("58291");
        assertThat(mapping.placeRef().stationName()).isEqualTo("MGBS");
        // JSONB is a parsed type — Postgres returns its own normalised rendering, so assert on
        // the payload rather than on the exact bytes written.
        assertThat(mapping.metadataJson().orElseThrow()).contains("\"platform\"", "\"7\"");

        // …and a reverse lookup turns an inbound provider id back into a RoadScanner one, which
        // is the seam Sprint 3 builds on.
        assertThat(mappingRepository.findByProviderCityId(flixbus, "58291").orElseThrow().locationId())
                .isEqualTo(locationId);

        // But none of that is visible on the public surface.
        ResponseEntity<String> http = rest.getForEntity("/api/v1/locations/{id}", String.class, id);
        assertThat(http.getBody()).doesNotContain("58291", "station-1", "FLIXBUS");
        assertThat(rest.getForEntity("/api/v1/locations?q={q}", String.class, displayName).getBody())
                .doesNotContain("58291", "station-1", "FLIXBUS");
    }
}
