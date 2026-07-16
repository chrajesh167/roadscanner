package com.roadscanner.authservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

/**
 * Operational tuning knobs for authentication policy — externalized rather than hardcoded
 * because docs/services/auth-service/security-design.md treats lockout thresholds, password
 * rules, and hashing cost as values expected to be tuned over time, not one-time decisions.
 * Defaults live in application.yml; the domain policies these feed stay value-agnostic.
 */
@ConfigurationProperties(prefix = "roadscanner.auth")
public record AuthProperties(
        int lockoutThreshold,
        Duration lockoutDuration,
        int passwordMinLength,
        int bcryptStrength,
        Duration resetTokenTtl
) {

    public AuthProperties {
        Objects.requireNonNull(lockoutDuration, "roadscanner.auth.lockout-duration must be set");
        Objects.requireNonNull(resetTokenTtl, "roadscanner.auth.reset-token-ttl must be set");
        if (lockoutThreshold < 1) {
            throw new IllegalArgumentException("roadscanner.auth.lockout-threshold must be positive");
        }
        if (passwordMinLength < 1) {
            throw new IllegalArgumentException("roadscanner.auth.password-min-length must be positive");
        }
        if (bcryptStrength < 10 || bcryptStrength > 31) {
            throw new IllegalArgumentException("roadscanner.auth.bcrypt-strength must be between 10 and 31");
        }
    }
}
