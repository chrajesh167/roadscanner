package com.roadscanner.providerintegrationservice;

import com.roadscanner.providerintegrationservice.adapter.out.security.EphemeralJwtKeyPair;
import com.roadscanner.providerintegrationservice.config.SecurityConfig;
import com.roadscanner.providerintegrationservice.testsupport.TestcontainersConfiguration;
import com.roadscanner.providerintegrationservice.testsupport.security.TestJwtIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The admin registry over real HTTP against real Postgres/Redis/Kafka, with genuinely signed
 * tokens — so the real JWT decoder and the role-claim→authority converter are both exercised,
 * not just the routing.
 *
 * <p>No {@code @Transactional}: a rollback would hide the commit-time behaviour (unique
 * constraints, V6 defaults) this test exists to prove. Each test therefore uses its own randomised
 * provider code rather than relying on a clean table.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ProviderRegistryEndToEndTest {

    @Autowired
    private TestRestTemplate rest;

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

    private static String uniqueCode() {
        return "E2E" + UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
    }

    private HttpHeaders headers(String role) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(tokens.issue(UUID.randomUUID(), role));
        return headers;
    }

    private HttpEntity<Map<String, Object>> adminBody(Map<String, Object> body) {
        return new HttpEntity<>(body, headers(SecurityConfig.ADMIN_ROLE));
    }

    private HttpEntity<Void> adminRequest() {
        return new HttpEntity<>(headers(SecurityConfig.ADMIN_ROLE));
    }

    private Map<String, Object> registerRequest(String code) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("category", "BUS");
        body.put("displayName", "E2E Provider " + code);
        body.put("capabilities", List.of("SEARCH", "HEALTH_CHECK"));
        body.put("baseUrl", "https://partner.example.test");
        body.put("timeoutMs", 6000);
        body.put("retryCount", 1);
        return body;
    }

    private Map<String, Object> register(String code) {
        ResponseEntity<Map> response =
                rest.postForEntity("/api/v1/providers", adminBody(registerRequest(code)), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    @Test
    void registersAProviderDisabledAndResolvesItById() {
        String code = uniqueCode();
        Map<String, Object> created = register(code);

        String id = (String) created.get("id");
        assertThat(id).isNotNull();
        assertThat(created.get("code")).isEqualTo(code);
        assertThat(created.get("category")).isEqualTo("BUS");
        assertThat(created.get("timeoutMs")).isEqualTo(6000);
        // Registering a row does not make a provider usable — enabling is a separate, deliberate act.
        assertThat(created.get("enabled")).isEqualTo(false);

        ResponseEntity<Map> fetched = rest.exchange("/api/v1/providers/{id}", HttpMethod.GET,
                adminRequest(), Map.class, id);
        assertThat(fetched.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(fetched.getBody().get("code")).isEqualTo(code);
    }

    @Test
    void rejectsADuplicateCodeWithAConflict() {
        String code = uniqueCode();
        register(code);

        ResponseEntity<String> second =
                rest.postForEntity("/api/v1/providers", adminBody(registerRequest(code)), String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void enableAndDisableAreIdempotentOverHttp() {
        String id = (String) register(uniqueCode()).get("id");

        for (int attempt = 0; attempt < 2; attempt++) {
            ResponseEntity<Map> enabled = rest.exchange("/api/v1/providers/{id}/enable", HttpMethod.POST,
                    adminRequest(), Map.class, id);
            assertThat(enabled.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(enabled.getBody().get("enabled")).isEqualTo(true);
        }

        for (int attempt = 0; attempt < 2; attempt++) {
            ResponseEntity<Map> disabled = rest.exchange("/api/v1/providers/{id}/disable", HttpMethod.POST,
                    adminRequest(), Map.class, id);
            assertThat(disabled.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(disabled.getBody().get("enabled")).isEqualTo(false);
        }

        // Disabling is not a delete — sessions and health rows reference this provider.
        assertThat(rest.exchange("/api/v1/providers/{id}", HttpMethod.GET, adminRequest(), Map.class, id)
                .getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updatesAProviderWithoutChangingItsCodeOrEnabledState() {
        String code = uniqueCode();
        String id = (String) register(code).get("id");
        rest.exchange("/api/v1/providers/{id}/enable", HttpMethod.POST, adminRequest(), Map.class, id);

        Map<String, Object> update = registerRequest(code);
        update.put("displayName", "Renamed Provider");
        update.put("category", "RAIL");
        update.put("timeoutMs", 2500);

        ResponseEntity<Map> updated = rest.exchange("/api/v1/providers/{id}", HttpMethod.PUT,
                adminBody(update), Map.class, id);

        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody().get("displayName")).isEqualTo("Renamed Provider");
        assertThat(updated.getBody().get("category")).isEqualTo("RAIL");
        assertThat(updated.getBody().get("timeoutMs")).isEqualTo(2500);
        // Identity is not editable, and an edit never silently takes a provider out of service.
        assertThat(updated.getBody().get("code")).isEqualTo(code);
        assertThat(updated.getBody().get("enabled")).isEqualTo(true);
    }

    @Test
    void storesCredentialsWriteOnly() {
        String id = (String) register(uniqueCode()).get("id");
        Map<String, Object> credentials = Map.of(
                "partnerEmail", "partner@roadscanner.com",
                "partnerPassword", "s3cret-value-e2e");

        ResponseEntity<String> stored = rest.exchange("/api/v1/providers/{id}/credentials", HttpMethod.PUT,
                adminBody(credentials), String.class, id);
        assertThat(stored.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(stored.getBody()).contains("\"hasPassword\":true").doesNotContain("s3cret-value-e2e");

        // Readable as presence only — an admin can replace secrets but never read them back.
        ResponseEntity<String> fetched = rest.exchange("/api/v1/providers/{id}/credentials", HttpMethod.GET,
                adminRequest(), String.class, id);
        assertThat(fetched.getBody())
                .contains("\"hasPassword\":true")
                .doesNotContain("s3cret-value-e2e", "partner@roadscanner.com");
    }

    @Test
    void reportsNotFoundWhenNoCredentialsExist() {
        String id = (String) register(uniqueCode()).get("id");

        assertThat(rest.exchange("/api/v1/providers/{id}/credentials", HttpMethod.GET,
                adminRequest(), String.class, id).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void theSeededFlixbusProviderIsPresentAndDisabled() {
        ResponseEntity<List> all = rest.exchange("/api/v1/providers", HttpMethod.GET, adminRequest(), List.class);

        List<Map<String, Object>> providers = all.getBody();
        Map<String, Object> flixbus = providers.stream()
                .filter(provider -> "FLIXBUS".equals(provider.get("code")))
                .findFirst()
                .orElseThrow();

        // V5 seeded it; V6 backfilled category and resilience settings and deliberately left it
        // disabled — its base URL is still a placeholder.
        assertThat(flixbus.get("enabled")).isEqualTo(false);
        assertThat(flixbus.get("category")).isEqualTo("BUS");
        assertThat(flixbus.get("timeoutMs")).isEqualTo(8000);
    }

    @Test
    void anUnknownProviderIsANotFoundOnEveryRoute() {
        UUID unknown = UUID.randomUUID();

        assertThat(rest.exchange("/api/v1/providers/{id}", HttpMethod.GET, adminRequest(), String.class, unknown)
                .getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.exchange("/api/v1/providers/{id}/enable", HttpMethod.POST, adminRequest(), String.class,
                unknown).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.exchange("/api/v1/providers/{id}/test", HttpMethod.POST, adminRequest(), String.class,
                unknown).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void testConnectionRunsTheRealProbeForASupportedProvider() {
        // MOCK is seeded enabled and has a real adapter, so this exercises the whole chain:
        // registry lookup -> CheckProviderHealth -> ProviderClientRegistry -> MockProviderClient.
        ResponseEntity<List> all = rest.exchange("/api/v1/providers", HttpMethod.GET, adminRequest(), List.class);
        String mockId = (String) ((List<Map<String, Object>>) all.getBody()).stream()
                .filter(provider -> "MOCK".equals(provider.get("code")))
                .findFirst()
                .orElseThrow()
                .get("id");

        ResponseEntity<Map> probed = rest.exchange("/api/v1/providers/{id}/test", HttpMethod.POST,
                adminRequest(), Map.class, mockId);

        assertThat(probed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(probed.getBody().get("providerType")).isEqualTo("MOCK");
        assertThat(probed.getBody().get("currentState")).isEqualTo("HEALTHY");
    }

    // ---------- Authorization, over real HTTP with genuinely signed tokens ----------

    @Test
    void theRegistryIsClosedToAnonymousCallers() {
        assertThat(rest.getForEntity("/api/v1/providers", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.postForEntity("/api/v1/providers",
                new HttpEntity<>(registerRequest(uniqueCode()), jsonOnly()), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void theRegistryIsForbiddenToANonAdmin() {
        HttpEntity<Map<String, Object>> traveler =
                new HttpEntity<>(registerRequest(uniqueCode()), headers("TRAVELER"));

        assertThat(rest.exchange("/api/v1/providers", HttpMethod.GET, traveler, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(rest.postForEntity("/api/v1/providers", traveler, String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void thePreExistingInternalSurfaceIsUnchanged() {
        // Adding admin security must not have closed the service-to-service surface every other
        // service already depends on.
        ResponseEntity<Map> health = rest.getForEntity(
                "/internal/api/v1/providers/MOCK/health", Map.class);

        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void observabilityAndContractSurfacesStayOpen() {
        assertThat(rest.getForEntity("/actuator/health", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> spec = rest.getForEntity("/v3/api-docs", String.class);
        assertThat(spec.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(spec.getBody())
                .contains("\"/api/v1/providers\"", "\"/api/v1/providers/{id}\"",
                        "Register a provider (admin)", "Test a provider connection (admin)")
                // The credentials response schema must document presence flags only.
                .contains("hasPassword", "hasToken");
    }

    private HttpHeaders jsonOnly() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}
