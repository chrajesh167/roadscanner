/**
 * Use-case orchestration implementing the inbound ports declared in domain.port.in,
 * coordinating domain objects and outbound ports. No HTTP, SQL, or Redis client types here —
 * only domain and port types (plus {@code @Transactional} boundaries; the layer is
 * framework-light, not framework-free, per implementation-roadmap.md step 3).
 *
 * Organized by feature per docs/services/auth-service/package-structure.md: registration,
 * login, token, passwordreset, role. Two kinds of classes per feature: implementations of the
 * inbound ports (pure orchestration over the command's domain objects), and flow services
 * ({@code TokenIssuer}, {@code TokenRefresher}, {@code SessionRevoker},
 * {@code PasswordResetConfirmer}) that resolve raw client input (a presented token string)
 * into the persisted domain objects the port commands require — the I/O the port Javadocs
 * explicitly assign to this layer.
 *
 * Classes carry no Spring stereotypes; they are wired explicitly in config.UseCaseConfig.
 */
package com.roadscanner.authservice.application.usecase;
