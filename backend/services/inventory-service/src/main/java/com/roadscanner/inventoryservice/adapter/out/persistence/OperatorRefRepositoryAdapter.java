package com.roadscanner.inventoryservice.adapter.out.persistence;

import com.roadscanner.inventoryservice.domain.model.OperatorRef;
import com.roadscanner.inventoryservice.domain.port.out.OperatorRefRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class OperatorRefRepositoryAdapter implements OperatorRefRepository {

    private final OperatorRefSpringDataRepository springDataRepository;
    private final OperatorRefMapper mapper = new OperatorRefMapper();

    OperatorRefRepositoryAdapter(OperatorRefSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<OperatorRef> findById(UUID operatorId) {
        return springDataRepository.findById(operatorId).map(mapper::toDomain);
    }

    @Override
    public OperatorRef save(OperatorRef operatorRef) {
        OperatorRefJpaEntity entity = springDataRepository.findById(operatorRef.operatorId())
                .map(existing -> {
                    mapper.applyTo(existing, operatorRef);
                    return existing;
                })
                .orElseGet(() -> mapper.toNewEntity(operatorRef));
        return mapper.toDomain(springDataRepository.save(entity));
    }
}
