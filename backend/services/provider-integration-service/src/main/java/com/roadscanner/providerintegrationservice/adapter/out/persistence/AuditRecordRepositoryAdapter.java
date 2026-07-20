package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import com.roadscanner.providerintegrationservice.domain.model.AuditRecord;
import com.roadscanner.providerintegrationservice.domain.port.out.AuditRecordRepository;
import org.springframework.stereotype.Repository;

@Repository
class AuditRecordRepositoryAdapter implements AuditRecordRepository {

    private final AuditRecordSpringDataRepository springDataRepository;
    private final AuditRecordMapper mapper = new AuditRecordMapper();

    AuditRecordRepositoryAdapter(AuditRecordSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public AuditRecord save(AuditRecord record) {
        return mapper.toDomain(springDataRepository.save(mapper.toNewEntity(record)));
    }
}
