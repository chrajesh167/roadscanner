package com.roadscanner.authservice.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Identifies one {@link PasswordResetRequest}. */
public record PasswordResetRequestId(UUID value) {

    public PasswordResetRequestId {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static PasswordResetRequestId generate() {
        return new PasswordResetRequestId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
