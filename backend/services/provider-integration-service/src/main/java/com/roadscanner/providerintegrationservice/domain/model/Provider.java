package com.roadscanner.providerintegrationservice.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * A configured provider — id, type, category, whether it's currently usable, which
 * {@link ProviderCapability capabilities} it supports, and how hard to try before giving up. This
 * is the platform's single record of "which providers exist and are turned on"; no other service
 * keeps one (docs/architecture/decisions/sprint-2-provider-foundation.md).
 *
 * <p>Separate from {@code ProviderClient}'s integration code: a provider can exist here with
 * {@code enabled=false} while its adapter class is fully implemented and tested — FLIXBUS's seed
 * row is exactly that, implemented but disabled until real credentials and a real base URL are
 * configured.
 *
 * <p>Sprint 2 made this aggregate mutable through explicit operations. It was previously
 * reconstitute-only, because providers were onboarded solely by Flyway seed; an admin API now
 * authors them, so the invariants that used to live implicitly in hand-written SQL live here
 * instead, where every caller gets them.
 */
public final class Provider {

    /** Retrying more than a handful of times turns one slow provider into a shared-pool outage. */
    public static final int MAX_RETRY_COUNT = 5;

    private final ProviderId id;
    private final ProviderType type;
    private ProviderCategory category;
    private String displayName;
    private boolean enabled;
    private Set<ProviderCapability> capabilities;
    private String baseUrl;
    private int timeoutMillis;
    private int retryCount;
    private final Instant createdAt;
    private Instant updatedAt;

    private Provider(ProviderId id, ProviderType type, ProviderCategory category, String displayName, boolean enabled,
                     Set<ProviderCapability> capabilities, String baseUrl, int timeoutMillis, int retryCount,
                     Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.displayName = requireNonBlank(displayName, "displayName");
        this.enabled = enabled;
        this.capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities must not be null"));
        this.baseUrl = baseUrl;
        this.timeoutMillis = requirePositiveTimeout(timeoutMillis);
        this.retryCount = requireValidRetryCount(retryCount);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /**
     * Registers a newly onboarded provider.
     *
     * <p>Starts <strong>disabled</strong>, always. A provider is only usable once its adapter,
     * base URL and credentials line up, and none of that is verifiable at the moment someone
     * POSTs a row — enabling is a separate, deliberate act after
     * {@code POST /providers/{id}/test} says the connection actually works.
     */
    public static Provider register(ProviderId id, ProviderType type, ProviderCategory category, String displayName,
                                    Set<ProviderCapability> capabilities, String baseUrl, int timeoutMillis,
                                    int retryCount, Instant now) {
        return new Provider(id, type, category, displayName, false, capabilities, baseUrl, timeoutMillis, retryCount,
                now, now);
    }

    /** Rehydrates from persisted state. Trusts the state is already valid. */
    public static Provider reconstitute(ProviderId id, ProviderType type, ProviderCategory category,
                                        String displayName, boolean enabled, Set<ProviderCapability> capabilities,
                                        String baseUrl, int timeoutMillis, int retryCount, Instant createdAt,
                                        Instant updatedAt) {
        return new Provider(id, type, category, displayName, enabled, capabilities, baseUrl, timeoutMillis, retryCount,
                createdAt, updatedAt);
    }

    /**
     * Applies an admin edit as a full-snapshot overwrite of the editable fields.
     *
     * <p>{@code type} is not editable: it is the provider's identity, the key
     * {@code ProviderClientRegistry} resolves an adapter by, and the value every
     * {@code provider_sessions} and {@code provider_health} row is keyed on. Renaming it would
     * silently orphan all of them.
     *
     * <p>{@code enabled} is not editable here either — enabling and disabling carry operational
     * meaning and have their own operations, so they are never an incidental side effect of a
     * rename.
     */
    public void update(ProviderCategory category, String displayName, Set<ProviderCapability> capabilities,
                       String baseUrl, int timeoutMillis, int retryCount, Instant now) {
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.displayName = requireNonBlank(displayName, "displayName");
        this.capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities must not be null"));
        this.baseUrl = baseUrl;
        this.timeoutMillis = requirePositiveTimeout(timeoutMillis);
        this.retryCount = requireValidRetryCount(retryCount);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * Turns the provider on. Idempotent, so a retried enable is not an error.
     *
     * @return true if this call changed the state
     */
    public boolean enable(Instant now) {
        if (enabled) {
            return false;
        }
        this.enabled = true;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        return true;
    }

    /**
     * Takes the provider out of service. Idempotent, for the same reason {@link #enable} is.
     *
     * <p>Deliberately not a delete: historical sessions, health records and audit rows reference
     * this provider and must stay resolvable, exactly as a withdrawn location does in
     * search-service.
     */
    public boolean disable(Instant now) {
        if (!enabled) {
            return false;
        }
        this.enabled = false;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        return true;
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

    private static int requirePositiveTimeout(int timeoutMillis) {
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("timeoutMillis must be greater than zero");
        }
        return timeoutMillis;
    }

    private static int requireValidRetryCount(int retryCount) {
        if (retryCount < 0 || retryCount > MAX_RETRY_COUNT) {
            throw new IllegalArgumentException("retryCount must be between 0 and " + MAX_RETRY_COUNT);
        }
        return retryCount;
    }

    public ProviderId id() {
        return id;
    }

    public ProviderType type() {
        return type;
    }

    public ProviderCategory category() {
        return category;
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

    public int timeoutMillis() {
        return timeoutMillis;
    }

    public int retryCount() {
        return retryCount;
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

    @Override
    public String toString() {
        return "Provider[" + type + " " + (enabled ? "enabled" : "disabled") + "]";
    }
}
