/**
 * Outbound ports the domain depends on without owning the implementation:
 * CredentialRepository, RefreshTokenRepository, PasswordResetRepository, PasswordHasher,
 * TokenSigner, RevocationCache.
 *
 * Intentionally empty as of this bootstrap — see
 * docs/services/auth-service/implementation-roadmap.md steps 2, 4-6.
 */
package com.roadscanner.authservice.domain.port.out;
