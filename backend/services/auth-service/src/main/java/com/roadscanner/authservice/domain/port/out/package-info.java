/**
 * Outbound ports the domain depends on without owning the implementation:
 * {@link com.roadscanner.authservice.domain.port.out.CredentialRepository},
 * {@link com.roadscanner.authservice.domain.port.out.RefreshTokenRepository},
 * {@link com.roadscanner.authservice.domain.port.out.PasswordResetRepository},
 * {@link com.roadscanner.authservice.domain.port.out.PasswordHasher},
 * {@link com.roadscanner.authservice.domain.port.out.TokenSigner}, and
 * {@link com.roadscanner.authservice.domain.port.out.RevocationCache}.
 *
 * Interfaces only. Implementations (JPA/Postgres, Redis, JWT signing) live in adapter.out.* and
 * are explicitly out of scope for this bootstrap — see
 * docs/services/auth-service/implementation-roadmap.md steps 4-6.
 */
package com.roadscanner.authservice.domain.port.out;
