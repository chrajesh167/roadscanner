package com.roadscanner.authservice.adapter.in.rest.passwordreset;

import com.roadscanner.authservice.application.usecase.passwordreset.PasswordResetConfirmer;
import com.roadscanner.authservice.domain.model.LoginIdentifier;
import com.roadscanner.authservice.domain.port.in.RequestPasswordReset;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Account-recovery endpoints. The request endpoint returns the identical 202 + body whether or
 * not the identifier exists — the enumeration-protection contract from api-contract.md,
 * honored down to the response shape. An identifier that fails structural validation (not a
 * plausible email/phone at all) still gets a 400: that reveals nothing about registration
 * state, only about the request's own shape.
 */
@RestController
@RequestMapping("/api/v1/auth/password-reset")
@Tag(name = "Password Reset", description = "Account recovery")
class PasswordResetController {

    private static final Map<String, String> GENERIC_ACCEPTED_BODY =
            Map.of("message", "If the identifier is registered, a password reset has been initiated.");

    private final RequestPasswordReset requestPasswordReset;
    private final PasswordResetConfirmer passwordResetConfirmer;

    PasswordResetController(RequestPasswordReset requestPasswordReset,
                            PasswordResetConfirmer passwordResetConfirmer) {
        this.requestPasswordReset = requestPasswordReset;
        this.passwordResetConfirmer = passwordResetConfirmer;
    }

    @PostMapping("/request")
    @Operation(summary = "Request a password reset",
            description = "Always accepted with the same response, whether or not the identifier is registered.")
    @ApiResponse(responseCode = "202", description = "Accepted (regardless of identifier existence)")
    ResponseEntity<Map<String, String>> request(@Valid @RequestBody RequestPasswordResetRequest request) {
        requestPasswordReset.request(new RequestPasswordReset.RequestPasswordResetCommand(
                new LoginIdentifier(request.identifier())));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(GENERIC_ACCEPTED_BODY);
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirm a password reset",
            description = "Consumes the single-use reset token, sets the new password, and revokes every session.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Password changed; all sessions revoked"),
            @ApiResponse(responseCode = "400", description = "Token invalid/expired/used, or password policy violation")
    })
    ResponseEntity<Void> confirm(@Valid @RequestBody ConfirmPasswordResetRequest request) {
        passwordResetConfirmer.confirm(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
