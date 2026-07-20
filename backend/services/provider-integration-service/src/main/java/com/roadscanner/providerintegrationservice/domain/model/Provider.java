package com.roadscanner.providerintegrationservice.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * A configured provider — id, type, whether it's currently usable, and which
 * {@link ProviderCapability capabilities} it supports. This is this service's own record of
 * "which providers exist and are turned on," separate from {@code ProviderClient}'s actual
 * integration code: a provider can exist here with {@code enabled=false} while its adapter class
 * is fully implemented and tested (see {@code FLIXBUS}'s seed row — implemented, but disabled
 * until real credentials/base URL are configured).
 */
public final class Provider {

    private final ProviderId id;
    private final ProviderType type;
    private String displayName;
    private boolean enabled;
    private final Set<ProviderCapability> capabilities;
    private String baseUrl;
    private final Instant createdAt;
    private Instant updatedAt;

    private Provider(ProviderId id, ProviderType type, String displayName, boolean enabled,
                      Set<ProviderCapability> capabilities, String baseUrl, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.displayName = requireNonBlank(displayName, "displayName");
        this.enabled = enabled;
        this.capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities must not be null"));
        this.baseUrl = baseUrl;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static Provider reconstitute(ProviderId id, ProviderType type, String displayName, boolean enabled,
                                         Set<ProviderCapability> capabilities, String baseUrl, Instant createdAt,
                                         Instant updatedAt) {
        return new Provider(id, type, displayName, enabled, capabilities, baseUrl, createdAt, updatedAt);
    }

    public boolean supports(ProviderCapability capability) {
        return capabilities.contains(capability);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public ProviderId id() {
        return id;
    }

    public ProviderType type() {
        return type;
    }

    public String displayName() {
        return displayName;
    }

    public boolean enabled() {
        return enabled;
    }

    public Set<ProviderCapability> capabilities() {
        return capabilities;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Provider other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
