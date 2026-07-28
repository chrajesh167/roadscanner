package com.roadscanner.paymentservice.adapter.in.rest;

import com.roadscanner.paymentservice.config.SecurityConfig;
import com.roadscanner.paymentservice.domain.model.RequesterContext;
import com.roadscanner.paymentservice.domain.model.Role;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/** Builds the framework-free {@link RequesterContext} every client-facing port needs from the
 * verified {@link Jwt} principal {@code SecurityConfig}'s filter chain already authenticated — the
 * one place a Spring Security type is translated into this service's own domain model
 * (docs/services/payment-service/boundaries.md's "Payment &harr; Auth"). */
public final class RequesterContextResolver {

    private RequesterContextResolver() {
    }

    public static RequesterContext from(Jwt jwt) {
        UUID subject = UUID.fromString(jwt.getSubject());
        String roleClaim = jwt.getClaimAsString(SecurityConfig.ROLE_CLAIM);
        if (roleClaim == null) {
            throw new IllegalStateException("JWT is missing the required '" + SecurityConfig.ROLE_CLAIM + "' claim");
        }
        return new RequesterContext(subject, Role.valueOf(roleClaim));
    }
}
