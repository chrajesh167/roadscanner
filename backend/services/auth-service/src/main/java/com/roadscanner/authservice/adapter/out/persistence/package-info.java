/**
 * JPA/Postgres implementations of the persistence-related repository ports
 * ({@link com.roadscanner.authservice.domain.port.out.CredentialRepository},
 * {@link com.roadscanner.authservice.domain.port.out.RefreshTokenRepository},
 * {@link com.roadscanner.authservice.domain.port.out.PasswordResetRepository},
 * {@link com.roadscanner.authservice.domain.port.out.RoleAssignmentRepository}).
 *
 * Three kinds of classes live here, each aware of only one side of the domain/JPA boundary:
 * <ul>
 *   <li>{@code *JpaEntity} — persistence shape only, zero {@code domain.model} imports.</li>
 *   <li>{@code *SpringDataRepository} — raw Spring Data JPA access, package-private.</li>
 *   <li>{@code *Mapper} — the only classes that see both {@code domain.model} and the entities.</li>
 *   <li>{@code *RepositoryAdapter} — implements the domain port, package-private (consumers
 *       depend on the port interface only).</li>
 * </ul>
 *
 * The RoleAssignment adapter is append-only (no {@code @Version}, no in-place mutation) —
 * see {@link com.roadscanner.authservice.adapter.out.persistence.RoleAssignmentRepositoryAdapter}.
 */
package com.roadscanner.authservice.adapter.out.persistence;
