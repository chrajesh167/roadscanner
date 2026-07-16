package com.roadscanner.authservice.adapter.in.rest.role;

import com.roadscanner.authservice.domain.model.AssignedBy;
import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.domain.port.in.AssignRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Internal role elevation (api-contract.md's Assign Role) — never client-facing; reachable
 * only with an ADMIN token, enforced both by the URL rule in SecurityConfig and by
 * {@code @PreAuthorize} here (defense in depth, per authentication-flow.md: a routing mistake
 * must never be the only guard). {@code assignedBy} is taken from the caller's own verified
 * token, never from the request body — an audit field an admin could type themselves would be
 * worthless as audit.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Roles", description = "Internal role management (admin only)")
class RoleController {

    private final AssignRole assignRole;
    private final Clock clock;

    RoleController(AssignRole assignRole, Clock clock) {
        this.assignRole = assignRole;
        this.clock = clock;
    }

    @PostMapping("/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Assign a role to a user",
            description = "Appends an immutable role assignment. Takes effect on the user's next token issuance.")
    @SecurityRequirement(name = "bearer-jwt")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Role assigned"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid access token"),
            @ApiResponse(responseCode = "403", description = "Caller is not an admin"),
            @ApiResponse(responseCode = "404", description = "No such user")
    })
    ResponseEntity<AssignRoleResponse> assign(@Valid @RequestBody AssignRoleRequest request,
                                              Authentication authentication) {
        AssignRole.AssignRoleResult result = assignRole.assign(new AssignRole.AssignRoleCommand(
                new UserId(UUID.fromString(request.userId())),
                Role.valueOf(request.role().toUpperCase()),
                AssignedBy.admin(new UserId(UUID.fromString(authentication.getName()))),
                Instant.now(clock)));
        return ResponseEntity.status(HttpStatus.CREATED).body(AssignRoleResponse.from(result.assignment()));
    }
}
