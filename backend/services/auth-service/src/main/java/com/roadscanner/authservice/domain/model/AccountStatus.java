package com.roadscanner.authservice.domain.model;

/**
 * A Credential's login eligibility. Two blocked states are kept distinct even though they
 * currently behave identically from the outside (both reject login with the same
 * {@link com.roadscanner.authservice.domain.exception.AccountLockedException}), because they
 * have different origins and different lifting mechanisms:
 *
 * <ul>
 *   <li>{@link #LOCKED} — system-imposed, temporary, caused by repeated failed login attempts
 *       (docs/services/auth-service/security-design.md, "brute-force protection"). Lifted by
 *       {@link Credential#unlock(java.time.Instant)}.</li>
 *   <li>{@link #SUSPENDED} — admin-imposed, does not expire on its own. Lifted only by
 *       {@link Credential#reinstate(java.time.Instant)}.</li>
 * </ul>
 */
public enum AccountStatus {
    ACTIVE,
    LOCKED,
    SUSPENDED
}
