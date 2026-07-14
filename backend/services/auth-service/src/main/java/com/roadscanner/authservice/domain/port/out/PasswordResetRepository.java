package com.roadscanner.authservice.domain.port.out;

import com.roadscanner.authservice.domain.model.PasswordResetRequest;
import com.roadscanner.authservice.domain.model.TokenHash;

import java.util.Optional;

/**
 * Persistence port for {@link PasswordResetRequest}. Implemented by a Postgres/JPA adapter in
 * adapter.out.persistence (not built today).
 */
public interface PasswordResetRepository {

    Optional<PasswordResetRequest> findByTokenHash(TokenHash tokenHash);

    PasswordResetRequest save(PasswordResetRequest request);
}
