package com.roadscanner.authservice.domain.port.in;

import com.roadscanner.authservice.domain.model.RefreshToken;

import java.time.Instant;
import java.util.Objects;

/** Ends exactly one session — "Logout". */
public interface RevokeSession {

    void revoke(RevokeSessionCommand command);

    record RevokeSessionCommand(RefreshToken token, Instant now) {
        public RevokeSessionCommand {
            Objects.requireNonNull(token, "token must not be null");
            Objects.requireNonNull(now, "now must not be null");
        }
    }
}
