package com.roadscanner.authservice.adapter.in.rest.registration;

import com.roadscanner.authservice.adapter.in.rest.token.AuthTokensResponse;
import com.roadscanner.authservice.application.usecase.token.TokenIssuer;
import com.roadscanner.authservice.domain.model.DeviceMetadata;
import com.roadscanner.authservice.domain.model.LoginIdentifier;
import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.port.in.RegisterUser;
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

/**
 * Registration endpoint. Composes the {@link RegisterUser} use case with {@link TokenIssuer}
 * so the new user is immediately logged in — the "register+login in one HTTP response is an
 * adapter/application-layer composition" that {@link RegisterUser}'s Javadoc anticipates. The
 * role in the issued token is the registration default ({@code TRAVELER}), which the use case
 * just assigned.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Registration", description = "Create a new identity")
class RegistrationController {

    private final RegisterUser registerUser;
    private final TokenIssuer tokenIssuer;

    RegistrationController(RegisterUser registerUser, TokenIssuer tokenIssuer) {
        this.registerUser = registerUser;
        this.tokenIssuer = tokenIssuer;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user",
            description = "Creates a credential with the default TRAVELER role and starts a session.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registered; session started"),
            @ApiResponse(responseCode = "400", description = "Malformed request or password policy violation"),
            @ApiResponse(responseCode = "409", description = "Identifier already registered")
    })
    ResponseEntity<AuthTokensResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterUser.RegistrationResult result = registerUser.register(
                new RegisterUser.RegisterUserCommand(new LoginIdentifier(request.identifier()), request.password()));
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthTokensResponse.from(
                tokenIssuer.issue(result.userId(), Role.TRAVELER, DeviceMetadata.of(request.deviceLabel()))));
    }
}
