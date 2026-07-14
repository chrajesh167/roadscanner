package com.roadscanner.authservice.domain.model;

import com.roadscanner.authservice.domain.exception.TokenExpiredException;
import com.roadscanner.authservice.domain.exception.TokenReusedException;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One link in a refresh-token rotation chain — a session. Encodes the rotation and
 * reuse-detection rule from docs/services/auth-service/security-design.md directly as an
 * aggregate invariant via {@link #rotate}, rather than leaving the application layer to
 * remember to check revocation status before rotating: a compromised or careless caller cannot
 * accidentally bypass this rule, because the only way to rotate at all is through this method.
 */
public final class RefreshToken {

    private final RefreshTokenId id;
    private final TokenHash tokenHash;
    private final UserId userId;
    private final Instant issuedAt;
    private final Instant expiresAt;
    private Instant revokedAt;
    private final RefreshTokenId replaces;
    private final DeviceMetadata deviceMetadata;

    private RefreshToken(RefreshTokenId id, TokenHash tokenHash, UserId userId, Instant issuedAt,
                          Instant expiresAt, Instant revokedAt, RefreshTokenId replaces, DeviceMetadata deviceMetadata) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
        this.revokedAt = revokedAt;
        this.replaces = replaces;
        this.deviceMetadata = Objects.requireNonNull(deviceMetadata, "deviceMetadata must not be null");
    }

    /** Issues a brand-new session — the head of a new rotation chain ({@code replaces} is empty). */
    public static RefreshToken issue(RefreshTokenId id, TokenHash tokenHash, UserId userId,
                                      Instant issuedAt, Instant expiresAt, DeviceMetadata deviceMetadata) {
        return new RefreshToken(id, tokenHash, userId, issuedAt, expiresAt, null, null, deviceMetadata);
    }

    /** Rehydrates a RefreshToken from persisted state. Trusts the state is already valid. */
    public static RefreshToken reconstitute(RefreshTokenId id, TokenHash tokenHash, UserId userId,
                                             Instant issuedAt, Instant expiresAt, Instant revokedAt,
                                             RefreshTokenId replaces, DeviceMetadata deviceMetadata) {
        return new RefreshToken(id, tokenHash, userId, issuedAt, expiresAt, revokedAt, replaces, deviceMetadata);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isActive(Instant now) {
        return !isRevoked() && !isExpired(now);
    }

    /** Idempotent — revoking an already-revoked token is a no-op, matching the platform's
     * general event-delivery model (docs/architecture/event-catalog.md: "SeatReleased can
     * legitimately fire twice... consumers must treat a second release as a no-op") applied
     * here to a domain method instead of an event consumer. */
    public void revoke(Instant now) {
        Objects.requireNonNull(now, "now must not be null");
        if (!isRevoked()) {
            this.revokedAt = now;
        }
    }

    /**
     * Exchanges this token for a new one, revoking this one in the same operation. This is the
     * single enforcement point for the platform's refresh-token reuse detection: a legitimate
     * client only ever holds the current, unrevoked token in its chain, so a second rotation
     * attempt against an already-revoked token is exactly the signal of a stolen, replayed
     * token described in docs/services/auth-service/security-design.md.
     *
     * @throws TokenReusedException if this token has already been rotated (or otherwise
     *         revoked) — the caller (application layer) is expected to respond by revoking the
     *         entire family via {@link com.roadscanner.authservice.domain.service.RefreshTokenFamilyPolicy}.
     * @throws TokenExpiredException if this token's validity window has already passed.
     */
    public RefreshToken rotate(RefreshTokenId newId, TokenHash newTokenHash, Instant now, Instant newExpiresAt) {
        Objects.requireNonNull(newId, "newId must not be null");
        Objects.requireNonNull(newTokenHash, "newTokenHash must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(newExpiresAt, "newExpiresAt must not be null");

        if (isRevoked()) {
            throw new TokenReusedException(id);
        }
        if (isExpired(now)) {
            throw new TokenExpiredException("Refresh token " + id + " has expired");
        }
        revoke(now);
        return new RefreshToken(newId, newTokenHash, this.userId, now, newExpiresAt, null, this.id, this.deviceMetadata);
    }

    public RefreshTokenId id() {
        return id;
    }

    public TokenHash tokenHash() {
        return tokenHash;
    }

    public UserId userId() {
        return userId;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Optional<Instant> revokedAt() {
        return Optional.ofNullable(revokedAt);
    }

    public Optional<RefreshTokenId> replaces() {
        return Optional.ofNullable(replaces);
    }

    public DeviceMetadata deviceMetadata() {
        return deviceMetadata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RefreshToken other)) return false;
        return id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
