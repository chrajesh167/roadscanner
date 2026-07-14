package com.roadscanner.authservice.domain.model;

import java.util.Optional;

/**
 * Minimal, optional descriptive metadata about the client/device a {@link RefreshToken} session
 * belongs to — deliberately just an opaque label, not a parsed user-agent. Exists so a future
 * "manage your active sessions" feature (docs/services/auth-service/implementation-roadmap.md,
 * "Future Extensibility") doesn't require a schema or domain-model change later — see
 * docs/services/auth-service/database-design.md.
 */
public record DeviceMetadata(String value) {

    public static DeviceMetadata unknown() {
        return new DeviceMetadata(null);
    }

    public static DeviceMetadata of(String label) {
        if (label == null || label.isBlank()) {
            return unknown();
        }
        return new DeviceMetadata(label);
    }

    public Optional<String> label() {
        return Optional.ofNullable(value);
    }
}
