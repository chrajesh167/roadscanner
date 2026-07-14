package com.roadscanner.authservice.adapter.out.persistence;

import com.roadscanner.authservice.domain.model.PasswordResetRequest;
import com.roadscanner.authservice.domain.model.PasswordResetRequestId;
import com.roadscanner.authservice.domain.model.TokenHash;
import com.roadscanner.authservice.domain.model.UserId;

/** See {@link CredentialMapper}'s Javadoc for the domain/JPA boundary this class sits on. */
final class PasswordResetRequestMapper {

    PasswordResetRequest toDomain(PasswordResetRequestJpaEntity entity) {
        return PasswordResetRequest.reconstitute(
                new PasswordResetRequestId(entity.getId()),
                new TokenHash(entity.getTokenHash()),
                new UserId(entity.getUserId()),
                entity.getExpiresAt(),
                entity.getUsedAt()
        );
    }

    PasswordResetRequestJpaEntity toNewEntity(PasswordResetRequest request) {
        return new PasswordResetRequestJpaEntity(
                request.id().value(),
                request.tokenHash().value(),
                request.userId().value(),
                request.expiresAt(),
                request.usedAt().orElse(null)
        );
    }

    /** Updates an already-managed entity in place — see
     * {@link PasswordResetRequestRepositoryAdapter} for why this is distinct from
     * {@link #toNewEntity}. */
    void applyTo(PasswordResetRequestJpaEntity entity, PasswordResetRequest request) {
        entity.applyMutableState(request.usedAt().orElse(null));
    }
}
