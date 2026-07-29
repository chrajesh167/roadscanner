package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import com.roadscanner.providerintegrationservice.domain.model.ProviderCredentials;
import com.roadscanner.providerintegrationservice.domain.model.ProviderCredentialsId;
import com.roadscanner.providerintegrationservice.domain.model.ProviderId;
import com.roadscanner.providerintegrationservice.domain.port.out.ProviderCredentialsRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/** Implements {@link ProviderCredentialsRepository} over Postgres via JPA. Package-private. */
@Repository
class ProviderCredentialsRepositoryAdapter implements ProviderCredentialsRepository {

    private final ProviderCredentialsSpringDataRepository springDataRepository;

    ProviderCredentialsRepositoryAdapter(ProviderCredentialsSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<ProviderCredentials> findByProvider(ProviderId providerId) {
        return springDataRepository.findByProviderId(providerId.value()).map(this::toDomain);
    }

    @Override
    public ProviderCredentials save(ProviderCredentials credentials) {
        ProviderCredentialsJpaEntity entity = springDataRepository.findById(credentials.id().value())
                .map(existing -> {
                    applyTo(existing, credentials);
                    return existing;
                })
                .orElseGet(() -> toEntity(credentials));

        return toDomain(springDataRepository.save(entity));
    }

    private ProviderCredentials toDomain(ProviderCredentialsJpaEntity entity) {
        return ProviderCredentials.reconstitute(
                new ProviderCredentialsId(entity.getId()),
                new ProviderId(entity.getProviderId()),
                entity.getPartnerEmail(),
                entity.getPartnerPassword(),
                entity.getPartnerToken(),
                entity.isEncrypted(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private ProviderCredentialsJpaEntity toEntity(ProviderCredentials credentials) {
        return new ProviderCredentialsJpaEntity(
                credentials.id().value(),
                credentials.providerId().value(),
                credentials.partnerEmail().orElse(null),
                credentials.partnerPassword().orElse(null),
                credentials.partnerToken().orElse(null),
                credentials.isEncrypted(),
                credentials.createdAt(),
                credentials.updatedAt());
    }

    private void applyTo(ProviderCredentialsJpaEntity entity, ProviderCredentials credentials) {
        entity.setPartnerEmail(credentials.partnerEmail().orElse(null));
        entity.setPartnerPassword(credentials.partnerPassword().orElse(null));
        entity.setPartnerToken(credentials.partnerToken().orElse(null));
        entity.setEncrypted(credentials.isEncrypted());
        entity.setUpdatedAt(credentials.updatedAt());
    }
}
