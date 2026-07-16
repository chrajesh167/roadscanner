package com.roadscanner.authservice.adapter.in.rest.token;

import com.roadscanner.authservice.application.usecase.token.SessionRevoker;
import com.roadscanner.authservice.application.usecase.token.TokenRefresher;
import com.roadscanner.authservice.domain.model.UserId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Session lifecycle endpoints: refresh (rotating, per security-design.md), logout (one
 * session, identified by the presented refresh token — no access token required, the refresh
 * token itself is the credential being surrendered), and logout-all (identity taken from the
 * authenticated access token, per api-contract.md).
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Tokens", description = "Refresh and revoke sessions")
class TokenController {

    private final TokenRefresher tokenRefresher;
    private final SessionRevoker sessionRevoker;

    TokenController(TokenRefresher tokenRefresher, SessionRevoker sessionRevoker) {
        this.tokenRefresher = tokenRefresher;
        this.sessionRevoker = sessionRevoker;
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh the session",
            description = "Exchanges a refresh token for a new access/refresh pair. The used refresh token is "
                    + "invalidated (rotation); presenting an already-rotated token revokes every session for the user.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New token pair issued"),
            @ApiResponse(responseCode = "401", description = "Refresh token unknown, expired, or already used")
    })
    AuthTokensResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return AuthTokensResponse.from(tokenRefresher.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Log out of the current session",
            description = "Revokes the presented refresh token. Idempotent — succeeds whether or not the token was still active.")
    @ApiResponse(responseCode = "204", description = "Session revoked (or was already inactive)")
    ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        sessionRevoker.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    @Operation(summary = "Log out everywhere",
            description = "Revokes every active session for the authenticated user.")
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "All sessions revoked"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token")
    })
    ResponseEntity<Void> logoutAll(Authentication authentication) {
        sessionRevoker.logoutAll(new UserId(UUID.fromString(authentication.getName())));
        return ResponseEntity.noContent().build();
    }
}
