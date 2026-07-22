package com.roadscanner.inventoryservice.domain.port.out;

import com.roadscanner.inventoryservice.domain.model.OperatorRef;

import java.util.Optional;
import java.util.UUID;

public interface OperatorRefRepository {

    Optional<OperatorRef> findById(UUID operatorId);

    OperatorRef save(OperatorRef operatorRef);
}
