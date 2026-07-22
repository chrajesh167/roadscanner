package com.roadscanner.inventoryservice.adapter.out.persistence;

import com.roadscanner.inventoryservice.domain.model.ProviderType;
import com.roadscanner.inventoryservice.domain.model.SyncRecord;
import com.roadscanner.inventoryservice.domain.port.out.SyncRecordRepository;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Repository
class SyncRecordRepositoryAdapter implements SyncRecordRepository {

    private final SyncRecordSpringDataRepository springDataRepository;
    private final SyncRecordMapper mapper = new SyncRecordMapper();

    SyncRecordRepositoryAdapter(SyncRecordSpringDataRepository springDataRepository) {
        this.springDataRepository = springDataRepository;
    }

    @Override
    public Optional<SyncRecord> findLatestByProviderType(ProviderType providerType) {
        return springDataRepository.findFirstByProviderTypeOrderByLastAttemptAtDesc(providerType.code())
                .map(mapper::toDomain);
    }

    @Override
    public java.util.List<SyncRecord> findAllLatest() {
        // One row per attempt is retained (data-ownership.md's history model); reduced here to
        // the single most-recent attempt per provider, since that's what operational visibility
        // actually needs — see GetSyncStatus's Javadoc.
        Map<String, SyncRecord> latestByProvider = new LinkedHashMap<>();
        for (SyncRecordJpaEntity entity : springDataRepository.findAllByOrderByLastAttemptAtDesc()) {
            latestByProvider.putIfAbsent(entity.getProviderType(), mapper.toDomain(entity));
        }
        return java.util.List.copyOf(latestByProvider.values());
    }

    @Override
    public SyncRecord save(SyncRecord record) {
        SyncRecordJpaEntity entity = springDataRepository.findById(record.id().value())
                .map(existing -> {
                    mapper.applyTo(existing, record);
                    return existing;
                })
                .orElseGet(() -> mapper.toNewEntity(record));
        return mapper.toDomain(springDataRepository.save(entity));
    }
}
