package com.roadscanner.providerintegrationservice.adapter.in.rest.admin;

import com.roadscanner.providerintegrationservice.adapter.in.rest.health.ProviderHealthResponse;
import com.roadscanner.providerintegrationservice.domain.model.Provider;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCapability;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCategory;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.in.ManageProviderCredentials;
import com.roadscanner.providerintegrationservice.domain.port.in.ManageProviders;
import com.roadscanner.providerintegrationservice.domain.port.in.RefreshProviderSession;
import com.roadscanner.providerintegrationservice.domain.port.in.TestProviderConnection;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Administrative management of the provider registry.
 *
 * <p>The registry is the platform's single answer to "which providers exist and are they enabled"
 * (docs/architecture/decisions/sprint-2-provider-foundation.md). Search-service and every other
 * service resolve provider questions through this service; none keeps its own copy.
 *
 * <p>Every route here requires {@code ROLE_ADMIN}, enforced by {@code SecurityConfig}. That is a
 * genuine change of posture for this service, whose pre-existing {@code /internal/**} surface is
 * unauthenticated and relies on the private network boundary — these routes are the first here
 * intended to be reachable by a human, so they are gated at the service rather than trusting
 * {@code api-gateway} alone.
 *
 * <p>Enabling is deliberately not part of create or update: a provider is registered disabled, and
 * turning it on is a separate act, ideally after {@code POST /{id}/test} confirms the connection
 * actually works.
 */
@RestController
@RequestMapping("/api/v1/providers")
@Tag(name = "Provider Administration", description = "The canonical RoadScanner provider registry (admin)")
class ProviderAdminController {

    private final ManageProviders manageProviders;
    private final ManageProviderCredentials manageCredentials;
    private final TestProviderConnection testProviderConnection;
    private final RefreshProviderSession refreshProviderSession;

    ProviderAdminController(ManageProviders manageProviders, ManageProviderCredentials manageCredentials,
                            TestProviderConnection testProviderConnection,
                            RefreshProviderSession refreshProviderSession) {
        this.manageProviders = manageProviders;
        this.manageCredentials = manageCredentials;
        this.testProviderConnection = testProviderConnection;
        this.refreshProviderSession = refreshProviderSession;
    }

    @GetMapping
    @Operation(summary = "List providers (admin)",
            description = "Every registered provider, or only those currently in service.")
    @ApiResponse(responseCode = "200", description = "The registry")
    List<ProviderResponse> list(
            @Parameter(description = "Return only providers that are currently enabled")
            @RequestParam(required = false, defaultValue = "false") boolean enabledOnly) {
        return manageProviders.list(enabledOnly).stream().map(ProviderResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a provider (admin)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The provider"),
            @ApiResponse(responseCode = "404", description = "No provider with this id")
    })
    ProviderResponse get(@PathVariable UUID id) {
        return ProviderResponse.from(manageProviders.get(new ProviderId(id)));
    }

    @PostMapping
    @Operation(summary = "Register a provider (admin)",
            description = "Adds a registry entry. Always starts disabled — registering a row does "
                    + "not make a provider usable, since an adapter implementing ProviderClient "
                    + "must also exist for it.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registered; Location header carries the new resource"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "409", description = "A provider with this code is already registered")
    })
    ResponseEntity<ProviderResponse> register(@Valid @RequestBody ProviderRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw new IllegalArgumentException("code is required when registering a provider");
        }

        Provider provider = manageProviders.register(new ManageProviders.RegisterProviderCommand(
                new ProviderType(request.code()),
                new ProviderCategory(request.category()),
                request.displayName(),
                toCapabilities(request.capabilities()),
                request.baseUrl(),
                request.timeoutMsOrDefault(),
                request.retryCountOrDefault()));

        ProviderResponse body = ProviderResponse.from(provider);
        URI location = UriComponentsBuilder.fromPath("/api/v1/providers/{id}").buildAndExpand(body.id()).toUri();
        return ResponseEntity.created(location).body(body);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a provider (admin)",
            description = "Full replace of the editable fields. The provider's code is not "
                    + "editable — sessions and health records are keyed on it. Use enable/disable "
                    + "to change whether it is in service.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The updated provider"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "No provider with this id")
    })
    ProviderResponse update(@PathVariable UUID id, @Valid @RequestBody ProviderRequest request) {
        Provider provider = manageProviders.update(new ManageProviders.UpdateProviderCommand(
                new ProviderId(id),
                new ProviderCategory(request.category()),
                request.displayName(),
                toCapabilities(request.capabilities()),
                request.baseUrl(),
                request.timeoutMsOrDefault(),
                request.retryCountOrDefault()));

        return ProviderResponse.from(provider);
    }

    @PostMapping("/{id}/enable")
    @Operation(summary = "Put a provider into service (admin)",
            description = "Idempotent: enabling an already-enabled provider succeeds.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The provider, now enabled"),
            @ApiResponse(responseCode = "404", description = "No provider with this id")
    })
    ProviderResponse enable(@PathVariable UUID id) {
        return ProviderResponse.from(manageProviders.enable(new ProviderId(id)).provider());
    }

    @PostMapping("/{id}/disable")
    @Operation(summary = "Take a provider out of service (admin)",
            description = "Idempotent. Not a delete — sessions, health records and audit rows "
                    + "reference this provider and must stay resolvable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The provider, now disabled"),
            @ApiResponse(responseCode = "404", description = "No provider with this id")
    })
    ProviderResponse disable(@PathVariable UUID id) {
        return ProviderResponse.from(manageProviders.disable(new ProviderId(id)).provider());
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Test a provider connection (admin)",
            description = "Runs the same live probe the health monitor runs and returns the "
                    + "resulting durable health record.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The health record after probing"),
            @ApiResponse(responseCode = "404", description = "No provider with this id"),
            @ApiResponse(responseCode = "502", description = "The provider could not be reached")
    })
    ProviderHealthResponse test(@PathVariable UUID id) {
        TestProviderConnection.Result result =
                testProviderConnection.test(new TestProviderConnection.Command(new ProviderId(id)));
        return ProviderHealthResponse.from(result.health());
    }

    @PostMapping("/{id}/refresh-session")
    @Operation(summary = "Refresh a provider session (admin)",
            description = "Authenticates afresh and returns the new session. Use after rotating "
                    + "credentials to prove the new secrets work before enabling the provider.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The new session"),
            @ApiResponse(responseCode = "404", description = "No provider with this id"),
            @ApiResponse(responseCode = "502", description = "The provider rejected the configured credentials")
    })
    ProviderSessionSummaryResponse refreshSession(@PathVariable UUID id) {
        RefreshProviderSession.Result result =
                refreshProviderSession.refresh(new RefreshProviderSession.Command(new ProviderId(id)));
        return new ProviderSessionSummaryResponse(result.sessionId().toString(), result.expiresAt());
    }

    @PutMapping("/{id}/credentials")
    @Operation(summary = "Replace a provider's credentials (admin)",
            description = "Write-only. Credentials can be replaced but never read back, so a "
                    + "compromised admin session cannot exfiltrate partner secrets.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credential presence and freshness — never the values"),
            @ApiResponse(responseCode = "400", description = "Neither a password nor a token was supplied"),
            @ApiResponse(responseCode = "404", description = "No provider with this id")
    })
    ProviderCredentialsResponse storeCredentials(@PathVariable UUID id,
                                                 @Valid @RequestBody ProviderCredentialsRequest request) {
        manageCredentials.store(new ManageProviderCredentials.StoreCredentialsCommand(
                new ProviderId(id), request.partnerEmail(), request.partnerPassword(), request.partnerToken()));

        return manageCredentials.summarise(new ProviderId(id))
                .map(ProviderCredentialsResponse::from)
                .orElseThrow(() -> new IllegalStateException("credentials were stored but could not be summarised"));
    }

    @GetMapping("/{id}/credentials")
    @Operation(summary = "Check a provider's credential state (admin)",
            description = "Reports only whether credentials exist and when they last changed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credential presence and freshness"),
            @ApiResponse(responseCode = "404", description = "No provider with this id, or no credentials stored")
    })
    ResponseEntity<ProviderCredentialsResponse> getCredentials(@PathVariable UUID id) {
        return manageCredentials.summarise(new ProviderId(id))
                .map(ProviderCredentialsResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    private static Set<ProviderCapability> toCapabilities(Set<String> raw) {
        return raw.stream()
                .map(String::strip)
                .map(ProviderCapability::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }
}
