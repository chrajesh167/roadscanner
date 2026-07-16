package com.roadscanner.authservice.application.usecase.token;

import com.roadscanner.authservice.domain.exception.InvalidCredentialsException;
import com.roadscanner.authservice.domain.exception.TokenReusedException;
import com.roadscanner.authservice.domain.model.RefreshToken;
import com.roadscanner.authservice.domain.model.RefreshTokenId;
import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.model.RoleAssignment;
import com.roadscanner.authservice.domain.model.TokenHash;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.domain.port.in.RefreshAccessToken;
import com.roadscanner.authservice.domain.port.out.RefreshTokenRepository;
import com.roadscanner.authservice.domain.port.out.RevocationCache;
import com.roadscanner.authservice.domain.port.out.RoleAssignmentRepository;
import com.roadscanner.authservice.domain.port.out.TokenGenerator;
import com.roadscanner.authservice.domain.port.out.TokenSigner;
import com.roadscanner.authservice.domain.service.RefreshTokenFamilyPolicy;
import com.roadscanner.authservice.domain.service.TokenExpiryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * The full refresh flow the REST adapter calls with the presented raw token: resolves it to
 * the persisted {@link RefreshToken} (the I/O {@link RefreshAccessToken}'s Javadoc assigns to
 * the application layer), delegates rotation to that port, and owns the compromise response —
 * on reuse detection, every session for the user is revoked before the failure propagates
 * (docs/services/auth-service/security-design.md's reuse-detection rule; revoking the user's
 * whole session set is a strict superset of the reused token's family, chosen because the
 * repository ports expose lookup by user, and over-revoking is the safe direction to err).
 *
 * {@code noRollbackFor = TokenReusedException}: the family revocation writes are the entire
 * point of that failure path and must commit even though the request itself fails.
 *
 * An unknown token hash raises the same {@link InvalidCredentialsException} used for login
 * failures — a refresh token is a credential, and revealing whether one exists would be the
 * same enumeration leak the login path protects against.
 */
@Transactional(noRollbackFor = TokenReusedException.class)
public class TokenRefresher {

    private static final Logger log = LoggerFactory.getLogger(TokenRefresher.class);

    private final RefreshAccessToken refreshAccessToken;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final RefreshTokenFamilyPolicy refreshTokenFamilyPolicy;
    private final RevocationCache revocationCache;
    private final TokenGenerator tokenGenerator;
    private final TokenSigner tokenSigner;
    private final TokenExpiryPolicy tokenExpiryPolicy;
    private final Clock clock;

    public TokenRefresher(RefreshAccessToken refreshAccessToken,
                          RefreshTokenRepository refreshTokenRepository,
                          RoleAssignmentRepository roleAssignmentRepository,
                          RefreshTokenFamilyPolicy refreshTokenFamilyPolicy,
                          RevocationCache revocationCache,
                          TokenGenerator tokenGenerator,
                          TokenSigner tokenSigner,
                          TokenExpiryPolicy tokenExpiryPolicy,
                          Clock clock) {
        this.refreshAccessToken = refreshAccessToken;
        this.refreshTokenRepository = refreshTokenRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.refreshTokenFamilyPolicy = refreshTokenFamilyPolicy;
        this.revocationCache = revocationCache;
        this.tokenGenerator = tokenGenerator;
        this.tokenSigner = tokenSigner;
        this.tokenExpiryPolicy = tokenExpiryPolicy;
        this.clock = clock;
    }

    public IssuedTokens refresh(String rawRefreshToken) {
        Instant now = Instant.now(clock);
        TokenHash presentedHash = tokenGenerator.hashOf(rawRefreshToken);
        RefreshToken current = refreshTokenRepository.findByTokenHash(presentedHash)
                .orElseThrow(InvalidCredentialsException::new);

        // Fast-path check against the derived Redis copy; the rotate() call below re-checks
        // against Postgres state, which stays authoritative (database-design.md).
        if (revocationCache.isRevoked(presentedHash) && !current.isRevoked()) {
            log.warn("Revocation cache and Postgres disagree for session {} — trusting Postgres", current.id());
        }

        TokenGenerator.GeneratedToken generated = tokenGenerator.generate();
        RefreshToken rotated;
        try {
            rotated = refreshAccessToken.refresh(new RefreshAccessToken.RefreshAccessTokenCommand(
                    current, RefreshTokenId.generate(), generated.tokenHash(), now,
                    tokenExpiryPolicy.refreshTokenExpiry(now))).newRefreshToken();
        } catch (TokenReusedException e) {
            revokeAllSessionsOf(current.userId(), now);
            log.error("Refresh token reuse detected for user {} (session {}) — revoked all sessions",
                    current.userId(), current.id());
            throw e;
        }

        Role role = currentRoleOf(current.userId());
        Instant accessTokenExpiresAt = tokenExpiryPolicy.accessTokenExpiry(now);
        String accessToken = tokenSigner.sign(current.userId(), role, now, accessTokenExpiresAt);
        log.info("Rotated session {} -> {} for user {}", current.id(), rotated.id(), current.userId());
        return new IssuedTokens(current.userId(), role, accessToken, accessTokenExpiresAt,
                generated.rawValue(), rotated.expiresAt());
    }

    private void revokeAllSessionsOf(UserId userId, Instant now) {
        List<RefreshToken> sessions = refreshTokenRepository.findByUserId(userId);
        refreshTokenFamilyPolicy.revokeFamily(sessions, now);
        sessions.forEach(session -> {
            refreshTokenRepository.save(session);
            revocationCache.markRevoked(session.tokenHash(), session.expiresAt());
        });
    }

    private Role currentRoleOf(UserId userId) {
        return roleAssignmentRepository.findLatestByUserId(userId)
                .map(RoleAssignment::role)
                .orElse(Role.TRAVELER);
    }
}
