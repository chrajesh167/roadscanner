package com.roadscanner.providerintegrationservice.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The partner credentials this service authenticates to one provider with.
 *
 * <p><strong>Secrets live here and nowhere else.</strong> Two rules make that enforceable rather
 * than aspirational:
 *
 * <ul>
 *   <li>{@link #toString()} never renders a secret. An aggregate that prints its own password
 *       ends up in a log line eventually, and no amount of care at call sites prevents it.</li>
 *   <li>No REST response maps from this type. The admin API reports only <em>whether</em>
 *       credentials exist and when they last changed — see {@code ProviderCredentialsResponse}.
 *       Writing them is allowed; reading them back is not.</li>
 * </ul>
 *
 * <p><strong>Encrypted at rest since Sprint 2.1.</strong> The aggregate deals in plaintext
 * throughout — encryption happens in the persistence layer via {@code EncryptedCredentialConverter}
 * — so nothing here has to remember to encrypt, and no future write path can forget to.
 * {@code encrypted} records that a row was written under that scheme; rows predating it decrypt
 * leniently and are re-stored encrypted on their next write.
 *
 * <p>A provider may authenticate by email/password, by a pre-issued token, or by both — which is
 * why all three fields are optional individually and only the "at least one" rule is enforced.
 */
public final class ProviderCredentials {

    private final ProviderCredentialsId id;
    private final ProviderId providerId;
    private String partnerEmail;
    private String partnerPassword;
    private String partnerToken;
    private boolean encrypted;
    private final Instant createdAt;
    private Instant updatedAt;

    private ProviderCredentials(ProviderCredentialsId id, ProviderId providerId, String partnerEmail,
                                String partnerPassword, String partnerToken, boolean encrypted,
                                Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.providerId = Objects.requireNonNull(providerId, "providerId must not be null");
        this.partnerEmail = normalise(partnerEmail);
        this.partnerPassword = normalise(partnerPassword);
        this.partnerToken = normalise(partnerToken);
        this.encrypted = encrypted;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        requireSomethingToAuthenticateWith();
    }

    public static ProviderCredentials issue(ProviderCredentialsId id, ProviderId providerId, String partnerEmail,
                                            String partnerPassword, String partnerToken, Instant now) {
        return new ProviderCredentials(id, providerId, partnerEmail, partnerPassword, partnerToken, true, now, now);
    }

    /** Rehydrates from persisted state. Trusts the state is already valid. */
    public static ProviderCredentials reconstitute(ProviderCredentialsId id, ProviderId providerId,
                                                   String partnerEmail, String partnerPassword, String partnerToken,
                                                   boolean encrypted, Instant createdAt, Instant updatedAt) {
        return new ProviderCredentials(id, providerId, partnerEmail, partnerPassword, partnerToken, encrypted,
                createdAt, updatedAt);
    }

    /** Replaces the stored secrets wholesale — a partial credential set authenticates nothing. */
    public void rotate(String partnerEmail, String partnerPassword, String partnerToken, Instant now) {
        this.partnerEmail = normalise(partnerEmail);
        this.partnerPassword = normalise(partnerPassword);
        this.partnerToken = normalise(partnerToken);
        // Rotations are written through the encrypting converter like every other write, so the
        // row is encrypted regardless of what the previous one was.
        this.encrypted = true;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        requireSomethingToAuthenticateWith();
    }

    public boolean hasPassword() {
        return partnerPassword != null;
    }

    public boolean hasToken() {
        return partnerToken != null;
    }

    private void requireSomethingToAuthenticateWith() {
        if (partnerPassword == null && partnerToken == null) {
            throw new IllegalArgumentException(
                    "credentials must carry at least a partnerPassword or a partnerToken");
        }
    }

    private static String normalise(String value) {
        return (value == null || value.isBlank()) ? null : value.strip();
    }

    public ProviderCredentialsId id() {
        return id;
    }

    public ProviderId providerId() {
        return providerId;
    }

    public Optional<String> partnerEmail() {
        return Optional.ofNullable(partnerEmail);
    }

    public Optional<String> partnerPassword() {
        return Optional.ofNullable(partnerPassword);
    }

    public Optional<String> partnerToken() {
        return Optional.ofNullable(partnerToken);
    }

    public boolean isEncrypted() {
        return encrypted;
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
        if (!(o instanceof ProviderCredentials other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /** Deliberately renders no secret — not the password, not the token, not even the email. */
    @Override
    public String toString() {
        return "ProviderCredentials[provider=" + providerId + ", password=" + (hasPassword() ? "set" : "absent")
                + ", token=" + (hasToken() ? "set" : "absent") + "]";
    }
}
