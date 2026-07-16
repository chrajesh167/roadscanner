package com.roadscanner.authservice.adapter.in.rest.role;

import jakarta.validation.constraints.NotBlank;

public record AssignRoleRequest(
        @NotBlank(message = "userId is required")
        String userId,

        @NotBlank(message = "role is required")
        String role
) {
}
