package com.roadscanner.providerintegrationservice.adapter.in.rest.session;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.port.in.AuthenticateProvider;
import com.roadscanner.providerintegrationservice.domain.port.in.RefreshSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Internal-only — opens and refreshes provider sessions. Every other controller in this package
 * takes the {@code sessionId} this issues as a path variable. No authentication is implemented
 * in this service itself; see README.md "Remaining Integration Points" for why (matching
 * {@code search-service}'s identical, disclosed gap for its own {@code /internal/**} surface).
 */
@RestController
@RequestMapping("/internal/api/v1/providers/{providerType}/sessions")
@Tag(name = "Provider Sessions", description = "Authenticate against a provider and refresh sessions")
class ProviderSessionController {

    private final AuthenticateProvider authenticateProvider;
    private final RefreshSession refreshSession;

    ProviderSessionController(AuthenticateProvider authenticateProvider, RefreshSession refreshSession) {
        this.authenticateProvider = authenticateProvider;
        this.refreshSession = refreshSession;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Authenticate against a provider", description = "Opens a new session; every other operation requires the returned sessionId.")
    AuthenticateProviderResponse authenticate(@PathVariable String providerType) {
        AuthenticateProvider.Result result = authenticateProvider.authenticate(
                new AuthenticateProvider.Command(new ProviderType(providerType)));
        return AuthenticateProviderResponse.from(result);
    }

    @PostMapping("/{sessionId}/refresh")
    @Operation(summary = "Refresh a session's token before it expires")
    RefreshSessionResponse refresh(@PathVariable String providerType, @PathVariable UUID sessionId) {
        RefreshSession.Result result = refreshSession.refresh(
                new RefreshSession.Command(new ProviderSessionId(sessionId)));
        return RefreshSessionResponse.from(result);
    }
}
