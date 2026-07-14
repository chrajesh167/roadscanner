package com.roadscanner.authservice.domain.port.in;

import com.roadscanner.authservice.domain.model.RefreshToken;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Ends every session for a user — "Logout All Sessions". Implemented via
 * {@link com.roadscanner.authservice.domain.service.RefreshTokenFamilyPolicy}, the same
 * mechanism reuse detection uses to revoke a compromised token family — see that class's
 * Javadoc for why they share one policy despite different triggers.
 */
public interface RevokeAllSessions {

    void revokeAll(RevokeAllSessionsCommand command);

    record RevokeAllSessionsCommand(List<RefreshToken> sessions, Instant now) {
        public RevokeAllSessionsCommand {
            Objects.requireNonNull(sessions, "sessions must not be null");
            Objects.requireNonNull(now, "now must not be null");
        }
    }
}
