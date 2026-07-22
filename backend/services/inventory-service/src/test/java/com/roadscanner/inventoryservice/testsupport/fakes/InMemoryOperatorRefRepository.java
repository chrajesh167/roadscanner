package com.roadscanner.inventoryservice.testsupport.fakes;

import com.roadscanner.inventoryservice.domain.model.OperatorRef;
import com.roadscanner.inventoryservice.domain.port.out.OperatorRefRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryOperatorRefRepository implements OperatorRefRepository {

    private final Map<UUID, OperatorRef> refs = new LinkedHashMap<>();

    @Override
    public Optional<OperatorRef> findById(UUID operatorId) {
        return Optional.ofNullable(refs.get(operatorId));
    }

    @Override
    public OperatorRef save(OperatorRef operatorRef) {
        refs.put(operatorRef.operatorId(), operatorRef);
        return operatorRef;
    }
}
