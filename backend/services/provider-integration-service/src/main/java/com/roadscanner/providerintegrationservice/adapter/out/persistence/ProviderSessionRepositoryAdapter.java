package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import com.roadscanner.providerintegrationservice.domain.model.ProviderSession;
import com.roadscanner.providerintegrationservice.domain.model.ProviderSessionId;
import com.roadscanner.providerintegrationservice.domain.model.SessionStatus;
import com.roadscanner.providerintegrationservice.domain.port.out.SessionRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Fetches-then-mutates on {@link #save} rather than always constructing a fresh entity, the
 * same optimistic-locking reason {@code SearchableTripRepositoryAdapter}'s Javadoc explains: a
 * fresh-entity save hands Hibernate no {@code @Version} value read from the database, silently
 * bypassing the check that guards against two concurrent updates to the same session (e.g. a
 * refresh racing an expiry sweep) clobbering each other. */
@Repository
class ProviderSessionRepositoryAdapter implements SessionRepository {

    private final ProviderSessionSpringDataRepository springDataRepository;
    private final ProviderSessionMapper mapper = new ProviderSessionMapper();

    ProviderSessionRepositoryAdapter(ProviderSessionSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<ProviderSession> findById(ProviderSessionId id) {
        return springDataRepository.findById(id.value()).map(mapper::toDomain);
    }

    @Override
    public ProviderSession save(ProviderSession session) {
        ProviderSessionJpaEntity entity = springDataRepository.findById(session.id().value())
                .map(existing -> {
                    mapper.applyTo(existing, session);
                    return existing;
                })
                .orElseGet(() -> mapper.toNewEntity(session));

        return mapper.toDomain(springDataRepository.save(entity));
    }

    @Override
    public List<ProviderSession> findActiveExpiredAsOf(Instant asOf) {
        return springDataRepository.findByStatusAndTokenExpiresAtLessThanEqual(SessionStatus.ACTIVE.name(), asOf)
                .stream().map(mapper::toDomain).toList();
    }
}
