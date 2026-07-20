package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderToken;
import com.roadscanner.providerintegrationservice.domain.model.ProviderType;
import com.roadscanner.providerintegrationservice.domain.model.SessionStatus;

final class ProviderSessionMapper {

    ProviderSession toDomain(ProviderSessionJpaEntity entity) {
        ProviderToken token = new ProviderToken(entity.getAccessToken(), entity.getRefreshToken(),
                entity.getTokenType(), entity.getTokenExpiresAt());
        return ProviderSession.reconstitute(new ProviderSessionId(entity.getId()),
                new ProviderType(entity.getProviderType()), SessionStatus.valueOf(entity.getStatus()), token,
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    ProviderSessionJpaEntity toNewEntity(ProviderSession session) {
        ProviderToken token = session.token();
        return new ProviderSessionJpaEntity(session.id().value(), session.providerType().code(),
                session.status().name(), token.accessToken(), token.refreshToken(), token.tokenType(),
                token.expiresAt(), session.createdAt(), session.updatedAt());
    }

    void applyTo(ProviderSessionJpaEntity entity, ProviderSession session) {
        ProviderToken token = session.token();
        entity.applyMutableState(session.status().name(), token.accessToken(), token.refreshToken(),
                token.tokenType(), token.expiresAt(), session.updatedAt());
    }
}
