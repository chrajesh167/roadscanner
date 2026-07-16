package com.roadscanner.authservice.adapter.in.rest.login;

import com.roadscanner.authservice.adapter.in.rest.token.AuthTokensResponse;
import com.roadscanner.authservice.application.usecase.token.TokenIssuer;
import com.roadscanner.authservice.domain.model.DeviceMetadata;
import com.roadscanner.authservice.domain.model.LoginIdentifier;
import com.roadscanner.authservice.domain.port.in.AuthenticateUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login endpoint: {@link AuthenticateUser} proves identity, {@link TokenIssuer} starts the
 * session — the composition {@link AuthenticateUser}'s Javadoc describes.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Login", description = "Authenticate and start a session")
class LoginController {

    private final AuthenticateUser authenticateUser;
    private final TokenIssuer tokenIssuer;

    LoginController(AuthenticateUser authenticateUser, TokenIssuer tokenIssuer) {
        this.authenticateUser = authenticateUser;
        this.tokenIssuer = tokenIssuer;
    }

    @PostMapping("/login")
    @Operation(summary = "Log in",
            description = "Verifies credentials and issues an access/refresh token pair. "
                    + "The failure response is identical for an unknown identifier and a wrong password.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated; session started"),
            @ApiResponse(responseCode = "400", description = "Malformed request"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "423", description = "Account temporarily locked")
    })
    AuthTokensResponse login(@Valid @RequestBody LoginRequest request) {
        AuthenticateUser.AuthenticationResult result = authenticateUser.authenticate(
                new AuthenticateUser.AuthenticateUserCommand(
                        new LoginIdentifier(request.identifier()), request.password()));
        return AuthTokensResponse.from(
                tokenIssuer.issue(result.userId(), result.role(), DeviceMetadata.of(request.deviceLabel())));
    }
}
