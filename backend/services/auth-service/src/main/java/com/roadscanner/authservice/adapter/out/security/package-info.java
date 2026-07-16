/**
 * Crypto adapters implementing the security-related outbound ports:
 * <ul>
 *   <li>{@link com.roadscanner.authservice.adapter.out.security.BCryptPasswordHasherAdapter} —
 *       PasswordHasher via BCrypt (adaptive, configurable cost).</li>
 *   <li>{@link com.roadscanner.authservice.adapter.out.security.OpaqueTokenGeneratorAdapter} —
 *       TokenGenerator via CSPRNG + SHA-256 (fast deterministic hash, deliberately unlike
 *       password hashing — see the port's Javadoc).</li>
 *   <li>{@link com.roadscanner.authservice.adapter.out.security.JwtTokenSignerAdapter} —
 *       TokenSigner via Nimbus RS256, with {@code kid} header support backed by
 *       {@link com.roadscanner.authservice.adapter.out.security.JwtKeyMaterial}.</li>
 * </ul>
 * See docs/services/auth-service/security-design.md for the decisions these implement.
 */
package com.roadscanner.authservice.adapter.out.security;
