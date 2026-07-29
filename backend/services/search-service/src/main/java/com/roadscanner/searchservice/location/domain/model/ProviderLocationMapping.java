package com.roadscanner.searchservice.location.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Translates one {@link Location} into one provider's own vocabulary — the single place in the
 * platform where a provider identifier is allowed to exist.
 *
 * <p>Example: RoadScanner's Hyderabad maps to FlixBus city {@code 58291}, station
 * {@code 3f2a…}, printed as "MGBS". A second provider may map the same Hyderabad to entirely
 * different ids, or to several stations. Because every mapping points at a {@link LocationId},
 * the rest of the platform can keep speaking only in RoadScanner ids.
 *
 * <p>Modelled as its own aggregate root rather than a child collection on {@link Location}
 * because its lifecycle is genuinely independent: mappings are created, re-verified and re-synced
 * by provider-facing processes on their own schedule, long after the location was authored, and
 * loading every provider's rows just to render an autocomplete suggestion would be wasteful.
 *
 * <p>Sprint 1 stores and reads these. It performs no provider I/O — {@link #markVerified} and
 * {@link #recordSync} exist so Sprint 3's reconciliation has domain operations to call rather
 * than mutating fields from outside.
 */
public final class ProviderLocationMapping {

    private final ProviderLocationMappingId id;
    private final ProviderCode provider;
    private final LocationId locationId;
    private ProviderPlaceRef placeRef;
    /** Raw provider payload, kept as an opaque JSON string. Deliberately not parsed here: its
     * shape is the provider's business, and giving it a domain type would couple this module to
     * whichever provider happened to be onboarded first. */
    private String metadataJson;
    private boolean verified;
    private Instant lastSynced;
    private final Instant createdAt;
    private Instant updatedAt;

    private ProviderLocationMapping(ProviderLocationMappingId id, ProviderCode provider, LocationId locationId,
                                    ProviderPlaceRef placeRef, String metadataJson, boolean verified,
                                    Instant lastSynced, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.locationId = Objects.requireNonNull(locationId, "locationId must not be null");
        this.placeRef = Objects.requireNonNull(placeRef, "placeRef must not be null");
        this.metadataJson = metadataJson;
        this.verified = verified;
        this.lastSynced = lastSynced;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    /** Records a newly discovered provider mapping. Always starts unverified — a mapping is a
     * claim until something confirms it. */
    public static ProviderLocationMapping create(ProviderLocationMappingId id, ProviderCode provider,
                                                 LocationId locationId, ProviderPlaceRef placeRef,
                                                 String metadataJson, Instant now) {
        return new ProviderLocationMapping(id, provider, locationId, placeRef, metadataJson, false, null, now, now);
    }

    /** Rehydrates from persisted state. Trusts the state is already valid. */
    public static ProviderLocationMapping reconstitute(ProviderLocationMappingId id, ProviderCode provider,
                                                       LocationId locationId, ProviderPlaceRef placeRef,
                                                       String metadataJson, boolean verified, Instant lastSynced,
                                                       Instant createdAt, Instant updatedAt) {
        return new ProviderLocationMapping(id, provider, locationId, placeRef, metadataJson, verified, lastSynced,
                createdAt, updatedAt);
    }

    /** Confirms the mapping is correct. Idempotent. */
    public boolean markVerified(Instant now) {
        if (verified) {
            return false;
        }
        this.verified = true;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        return true;
    }

    /**
     * Applies a fresh snapshot from the provider and stamps the sync time.
     *
     * <p>Re-syncing clears {@code verified} whenever the provider's identifiers actually changed:
     * a previous human confirmation applied to the old identifiers and cannot be assumed to carry
     * over. A no-change re-sync keeps the flag, so routine polling doesn't churn it.
     */
    public void recordSync(ProviderPlaceRef refreshed, String refreshedMetadataJson, Instant now) {
        Objects.requireNonNull(refreshed, "refreshed must not be null");
        if (!refreshed.equals(this.placeRef)) {
            this.verified = false;
        }
        this.placeRef = refreshed;
        this.metadataJson = refreshedMetadataJson;
        this.lastSynced = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public ProviderLocationMappingId id() {
        return id;
    }

    public ProviderCode provider() {
        return provider;
    }

    public LocationId locationId() {
        return locationId;
    }

    public ProviderPlaceRef placeRef() {
        return placeRef;
    }

    public Optional<String> metadataJson() {
        return Optional.ofNullable(metadataJson);
    }

    public boolean isVerified() {
        return verified;
    }

    public Optional<Instant> lastSynced() {
        return Optional.ofNullable(lastSynced);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof ProviderLocationMapping mapping && id.equals(mapping.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "ProviderLocationMapping[" + provider + " -> " + locationId + "]";
    }
}
