/**
 * Inbound use-case ports: {@link com.roadscanner.authservice.domain.port.in.RegisterUser},
 * {@link com.roadscanner.authservice.domain.port.in.AuthenticateUser},
 * {@link com.roadscanner.authservice.domain.port.in.RefreshAccessToken},
 * {@link com.roadscanner.authservice.domain.port.in.RevokeSession},
 * {@link com.roadscanner.authservice.domain.port.in.RevokeAllSessions},
 * {@link com.roadscanner.authservice.domain.port.in.RequestPasswordReset},
 * {@link com.roadscanner.authservice.domain.port.in.ConfirmPasswordReset}, and
 * {@link com.roadscanner.authservice.domain.port.in.AssignRole}.
 *
 * Interfaces only — see docs/services/auth-service/api-contract.md for the use cases these
 * represent. Implementations (application.usecase) are explicitly out of scope for this
 * bootstrap; see docs/services/auth-service/implementation-roadmap.md step 3. Each interface's
 * Command/Result records are built entirely from domain types, never a raw signed token or raw
 * secret — see each interface's own Javadoc for why.
 */
package com.roadscanner.authservice.domain.port.in;
