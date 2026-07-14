package com.roadscanner.authservice.adapter.out.persistence;

import com.roadscanner.authservice.domain.model.DeviceMetadata;
import com.roadscanner.authservice.domain.model.RefreshToken;
import com.roadscanner.authservice.domain.model.RefreshTokenId;
import com.roadscanner.authservice.domain.model.TokenHash;
import com.roadscanner.authservice.domain.model.UserId;

/** See {@link CredentialMapper}'s Javadoc for the domain/JPA boundary this class sits on. */
final class RefreshTokenMapper {

    RefreshToken toDomain(RefreshTokenJpaEntity entity) {
        return RefreshToken.reconstitute(
                new RefreshTokenId(entity.getId()),
                new TokenHash(entity.getTokenHash()),
                new UserId(entity.getUserId()),
                entity.getIssuedAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getReplacesTokenId() == null ? null : new RefreshTokenId(entity.getReplacesTokenId()),
                DeviceMetadata.of(entity.getDeviceLabel())
        );
    }

    RefreshTokenJpaEntity toNewEntity(RefreshToken refreshToken) {
        return new RefreshTokenJpaEntity(
                refreshToken.id().value(),
                refreshToken.tokenHash().value(),
                refreshToken.userId().value(),
                refreshToken.issuedAt(),
                refreshToken.expiresAt(),
                refreshToken.revokedAt().orElse(null),
                refreshToken.replaces().map(RefreshTokenId::value).orElse(null),
                refreshToken.deviceMetadata().label().orElse(null)
        );
    }

    /** Updates an already-managed entity in place — see {@link RefreshTokenRepositoryAdapter}
     * for why this is distinct from {@link #toNewEntity}. */
    void applyTo(RefreshTokenJpaEntity entity, RefreshToken refreshToken) {
        entity.applyMutableState(refreshToken.revokedAt().orElse(null));
    }
}
