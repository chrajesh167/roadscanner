package com.roadscanner.authservice.testsupport.fakes;

import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.domain.port.out.TokenSigner;

import java.time.Instant;

/** TokenSigner double producing an inspectable, unsigned string — real RS256 signing is the
 * adapter's concern, covered by its own test. */
public final class StubTokenSigner implements TokenSigner {

    @Override
    public String sign(UserId userId, Role role, Instant issuedAt, Instant expiresAt) {
        return "signed:" + userId + ":" + role + ":" + expiresAt;
    }
}
