package com.roadscanner.authservice.application.usecase.token;

import com.roadscanner.authservice.domain.model.RefreshToken;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.domain.port.in.RevokeAllSessions;
import com.roadscanner.authservice.domain.port.in.RevokeSession;
import com.roadscanner.authservice.domain.port.out.RefreshTokenRepository;
import com.roadscanner.authservice.domain.port.out.TokenGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * The logout flows the REST adapter calls: resolves raw input (a presented refresh token, or
 * the caller's identity from the access token) into the domain objects the
 * {@link RevokeSession}/{@link RevokeAllSessions} port commands require.
 *
 * Logout with an unknown or already-revoked token deliberately succeeds silently: revocation
 * is idempotent, the client's goal (that the token no longer works) is already true, and a
 * distinct "token not found" response would leak whether a stolen token was still valid —
 * the same enumeration reasoning as everywhere else in this service.
 */
@Transactional
public class SessionRevoker {

    private static final Logger log = LoggerFactory.getLogger(SessionRevoker.class);

    private final RevokeSession revokeSession;
    private final RevokeAllSessions revokeAllSessions;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenGenerator tokenGenerator;
    private final Clock clock;

    public SessionRevoker(RevokeSession revokeSession,
                          RevokeAllSessions revokeAllSessions,
                          RefreshTokenRepository refreshTokenRepository,
                          TokenGenerator tokenGenerator,
                          Clock clock) {
        this.revokeSession = revokeSession;
        this.revokeAllSessions = revokeAllSessions;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenGenerator = tokenGenerator;
        this.clock = clock;
    }

    public void logout(String rawRefreshToken) {
        Instant now = Instant.now(clock);
        refreshTokenRepository.findByTokenHash(tokenGenerator.hashOf(rawRefreshToken))
                .ifPresent(token -> {
                    revokeSession.revoke(new RevokeSession.RevokeSessionCommand(token, now));
                    log.info("Logout — revoked session {} for user {}", token.id(), token.userId());
                });
    }

    public void logoutAll(UserId userId) {
        Instant now = Instant.now(clock);
        List<RefreshToken> sessions = refreshTokenRepository.findByUserId(userId);
        revokeAllSessions.revokeAll(new RevokeAllSessions.RevokeAllSessionsCommand(sessions, now));
        log.info("Logout everywhere — revoked {} session(s) for user {}", sessions.size(), userId);
    }
}
