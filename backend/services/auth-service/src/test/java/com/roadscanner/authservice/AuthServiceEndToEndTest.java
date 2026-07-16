package com.roadscanner.authservice;

import com.roadscanner.authservice.domain.model.AssignedBy;
import com.roadscanner.authservice.domain.model.PasswordResetRequest;
import com.roadscanner.authservice.domain.model.PasswordResetRequestId;
import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.model.RoleAssignment;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.domain.port.out.PasswordResetRepository;
import com.roadscanner.authservice.domain.port.out.RoleAssignmentRepository;
import com.roadscanner.authservice.domain.port.out.TokenGenerator;
import com.roadscanner.authservice.domain.port.out.TokenSigner;
import com.roadscanner.authservice.testsupport.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Use-case-level tests over the real HTTP surface, per testing-strategy.md: full flows
 * (register → login → refresh → logout) against real Postgres and Redis, plus every scenario
 * in that document's "Security-Specific Scenarios (Explicit, Non-Optional)" list.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AuthServiceEndToEndTest {

    private static final String STRONG_PASSWORD = "correct-horse-7-battery";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    private PasswordResetRepository passwordResetRepository;

    @Autowired
    private TokenGenerator tokenGenerator;

    @Autowired
    private TokenSigner tokenSigner;

    // --- helpers -----------------------------------------------------------

    private static String uniqueIdentifier() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody();
    }

    private ResponseEntity<Map> post(String path, Map<String, ?> requestBody) {
        return rest.postForEntity(path, requestBody, Map.class);
    }

    private ResponseEntity<Map> postAuthorized(String path, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(Map.of(), headers), Map.class);
    }

    private Map<String, Object> register(String identifier) {
        ResponseEntity<Map> response = post("/api/v1/auth/register",
                Map.of("identifier", identifier, "password", STRONG_PASSWORD, "deviceLabel", "e2e-test"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return body(response);
    }

    private ResponseEntity<Map> login(String identifier, String password) {
        return post("/api/v1/auth/login", Map.of("identifier", identifier, "password", password));
    }

    private ResponseEntity<Map> refresh(String refreshToken) {
        return post("/api/v1/auth/refresh", Map.of("refreshToken", refreshToken));
    }

    // --- full flows ----------------------------------------------------------

    @Test
    void registerLoginRefreshLogoutFullLifecycle() {
        String identifier = uniqueIdentifier();

        Map<String, Object> registered = register(identifier);
        assertThat(registered.get("role")).isEqualTo("TRAVELER");
        assertThat(registered.get("tokenType")).isEqualTo("Bearer");
        assertThat(registered.get("accessToken")).isNotNull();
        assertThat(registered.get("refreshToken")).isNotNull();

        ResponseEntity<Map> loginResponse = login(identifier, STRONG_PASSWORD);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String refreshToken = (String) body(loginResponse).get("refreshToken");

        ResponseEntity<Map> refreshed = refresh(refreshToken);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        String rotatedToken = (String) body(refreshed).get("refreshToken");
        assertThat(rotatedToken).isNotEqualTo(refreshToken);

        ResponseEntity<Map> logout = post("/api/v1/auth/logout", Map.of("refreshToken", rotatedToken));
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(refresh(rotatedToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // Logout is idempotent.
        assertThat(post("/api/v1/auth/logout", Map.of("refreshToken", rotatedToken)).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void duplicateRegistrationIsRejectedWithConflict() {
        String identifier = uniqueIdentifier();
        register(identifier);

        ResponseEntity<Map> second = post("/api/v1/auth/register",
                Map.of("identifier", identifier, "password", STRONG_PASSWORD));
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void weakPasswordIsRejectedWithTheSpecificRule() {
        ResponseEntity<Map> response = post("/api/v1/auth/register",
                Map.of("identifier", uniqueIdentifier(), "password", "short1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) body(response).get("message")).contains("12 characters");
    }

    @Test
    void structurallyInvalidRequestsFailFastWithFieldErrors() {
        ResponseEntity<Map> response = post("/api/v1/auth/register", Map.of("identifier", ""));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(body(response).get("fieldErrors")).isNotNull();
    }

    // --- enumeration protection ----------------------------------------------

    @Test
    void unknownIdentifierAndWrongPasswordAreIndistinguishable() {
        String identifier = uniqueIdentifier();
        register(identifier);

        ResponseEntity<Map> unknownIdentifier = login(uniqueIdentifier(), STRONG_PASSWORD);
        ResponseEntity<Map> wrongPassword = login(identifier, "wrong-password-11");

        assertThat(unknownIdentifier.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(body(unknownIdentifier).get("message")).isEqualTo(body(wrongPassword).get("message"));
        assertThat(body(unknownIdentifier).get("error")).isEqualTo(body(wrongPassword).get("error"));
    }

    @Test
    void passwordResetRequestIsIdenticalForKnownAndUnknownIdentifiers() {
        String identifier = uniqueIdentifier();
        register(identifier);

        ResponseEntity<Map> known = post("/api/v1/auth/password-reset/request", Map.of("identifier", identifier));
        ResponseEntity<Map> unknown = post("/api/v1/auth/password-reset/request", Map.of("identifier", uniqueIdentifier()));

        assertThat(known.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(known.getBody()).isEqualTo(unknown.getBody());
    }

    // --- lockout ---------------------------------------------------------------

    @Test
    void accountLocksAfterRepeatedFailuresAndRejectsEvenTheCorrectPassword() {
        String identifier = uniqueIdentifier();
        register(identifier);

        for (int i = 0; i < 5; i++) { // roadscanner.auth.lockout-threshold
            assertThat(login(identifier, "wrong-password-11").getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        assertThat(login(identifier, STRONG_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.LOCKED);
    }

    // --- rotation & reuse detection ---------------------------------------------

    @Test
    void reuseOfARotatedTokenRevokesEverySessionForTheUser() {
        String identifier = uniqueIdentifier();
        Map<String, Object> session = register(identifier);
        String originalRefresh = (String) session.get("refreshToken");

        String rotatedRefresh = (String) body(refresh(originalRefresh)).get("refreshToken");

        // Basic rotation invariant: the used token no longer works...
        assertThat(refresh(originalRefresh).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // ...and the reuse attempt above must have revoked the whole family, including the
        // still-current successor — the compromise response from security-design.md.
        assertThat(refresh(rotatedRefresh).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logoutAllRevokesEveryDeviceSession() {
        String identifier = uniqueIdentifier();
        Map<String, Object> deviceA = register(identifier);
        Map<String, Object> deviceB = body(login(identifier, STRONG_PASSWORD));

        ResponseEntity<Map> response = postAuthorized("/api/v1/auth/logout-all",
                (String) deviceB.get("accessToken"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(refresh((String) deviceA.get("refreshToken")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(refresh((String) deviceB.get("refreshToken")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // --- access-token verification ------------------------------------------------

    @Test
    void protectedEndpointRejectsMissingExpiredAndTamperedTokens() {
        assertThat(postAuthorized("/api/v1/auth/logout-all", null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        String expired = tokenSigner.sign(UserId.generate(), Role.TRAVELER,
                Instant.now().minus(Duration.ofHours(2)), Instant.now().minus(Duration.ofHours(1)));
        assertThat(postAuthorized("/api/v1/auth/logout-all", expired).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);

        String valid = (String) register(uniqueIdentifier()).get("accessToken");
        assertThat(postAuthorized("/api/v1/auth/logout-all", forgeRoleClaim(valid)).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /** Privilege-escalates the payload while keeping valid JWT structure — the signature no longer matches. */
    private String forgeRoleClaim(String token) {
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]))
                .replace("TRAVELER", "ADMIN");
        parts[1] = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes());
        return String.join(".", parts);
    }

    // --- RBAC -------------------------------------------------------------------

    @Test
    void roleAssignmentRequiresAdminAndTakesEffectOnNextLogin() {
        String adminIdentifier = uniqueIdentifier();
        String targetIdentifier = uniqueIdentifier();
        UserId adminId = new UserId(UUID.fromString((String) register(adminIdentifier).get("userId")));
        String targetUserId = (String) register(targetIdentifier).get("userId");

        // A TRAVELER token must not reach the role endpoint (403, not 401 — authenticated but
        // not authorized).
        String travelerToken = (String) body(login(targetIdentifier, STRONG_PASSWORD)).get("accessToken");
        HttpHeaders travelerHeaders = new HttpHeaders();
        travelerHeaders.setBearerAuth(travelerToken);
        ResponseEntity<Map> forbidden = rest.exchange("/api/v1/auth/roles", HttpMethod.POST,
                new HttpEntity<>(Map.of("userId", targetUserId, "role", "OPERATOR"), travelerHeaders), Map.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Provision ADMIN operationally (security-design.md: "ADMIN/SUPPORT are provisioned
        // operationally, not self-service") — here, directly through the repository port.
        roleAssignmentRepository.save(RoleAssignment.assign(
                adminId, Role.ADMIN, AssignedBy.service("e2e-provisioning"), Instant.now().plusSeconds(1)));
        String adminToken = (String) body(login(adminIdentifier, STRONG_PASSWORD)).get("accessToken");

        HttpHeaders adminHeaders = new HttpHeaders();
        adminHeaders.setBearerAuth(adminToken);
        ResponseEntity<Map> assigned = rest.exchange("/api/v1/auth/roles", HttpMethod.POST,
                new HttpEntity<>(Map.of("userId", targetUserId, "role", "OPERATOR"), adminHeaders), Map.class);
        assertThat(assigned.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(body(assigned).get("assignedBy")).isEqualTo("admin:" + adminId);

        // The elevation appears in the next issued token, not retroactively.
        assertThat(body(login(targetIdentifier, STRONG_PASSWORD)).get("role")).isEqualTo("OPERATOR");

        // Unknown target → 404 from the internal surface.
        ResponseEntity<Map> notFound = rest.exchange("/api/v1/auth/roles", HttpMethod.POST,
                new HttpEntity<>(Map.of("userId", UUID.randomUUID().toString(), "role", "OPERATOR"), adminHeaders),
                Map.class);
        assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // --- password reset ------------------------------------------------------------

    @Test
    void passwordResetConfirmChangesPasswordOnceAndRevokesSessions() {
        String identifier = uniqueIdentifier();
        Map<String, Object> session = register(identifier);
        UserId userId = new UserId(UUID.fromString((String) session.get("userId")));

        // Mint the reset token through the same ports the service uses — raw-token delivery
        // is notification-service's job and doesn't exist yet (responsibilities.md).
        TokenGenerator.GeneratedToken generated = tokenGenerator.generate();
        passwordResetRepository.save(PasswordResetRequest.issue(
                PasswordResetRequestId.generate(), generated.tokenHash(), userId,
                Instant.now().plus(Duration.ofMinutes(30))));

        String newPassword = "reset-password-99z";
        ResponseEntity<Map> confirm = post("/api/v1/auth/password-reset/confirm",
                Map.of("token", generated.rawValue(), "newPassword", newPassword));
        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // Old password dead, new one works, pre-reset session revoked, token single-use.
        assertThat(login(identifier, STRONG_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(login(identifier, newPassword).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refresh((String) session.get("refreshToken")).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(post("/api/v1/auth/password-reset/confirm",
                Map.of("token", generated.rawValue(), "newPassword", "again-password-3x")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // --- observability & contract surface -----------------------------------------

    @Test
    void healthAndOpenApiAreServedWithoutAuthentication() {
        assertThat(rest.getForEntity("/actuator/health", Map.class).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> apiDocs = rest.getForEntity("/v3/api-docs", String.class);
        assertThat(apiDocs.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(apiDocs.getBody())
                .contains("/api/v1/auth/register")
                .contains("/api/v1/auth/refresh")
                .contains("bearer-jwt");
    }
}
