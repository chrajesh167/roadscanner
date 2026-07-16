package com.roadscanner.authservice.application.usecase.token;

import com.roadscanner.authservice.domain.model.DeviceMetadata;
import com.roadscanner.authservice.domain.model.RefreshToken;
import com.roadscanner.authservice.domain.model.RefreshTokenId;
import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.domain.port.out.RefreshTokenRepository;
import com.roadscanner.authservice.domain.port.out.TokenGenerator;
import com.roadscanner.authservice.domain.port.out.TokenSigner;
import com.roadscanner.authservice.domain.service.TokenExpiryPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Starts a session: issues the access/refresh token pair handed back by Register and Login.
 * This is the "related but separate concern the application layer composes on top of a
 * successful authentication" from {@link com.roadscanner.authservice.domain.port.in.AuthenticateUser}'s
 * Javadoc — issuance policy is auth-service's own business logic
 * (docs/services/auth-service/package-structure.md, "Shared Library Usage").
 */
@Transactional
public class TokenIssuer {

    private static final Logger log = LoggerFactory.getLogger(TokenIssuer.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenGenerator tokenGenerator;
    private final TokenSigner tokenSigner;
    private final TokenExpiryPolicy tokenExpiryPolicy;
    private final Clock clock;

    public TokenIssuer(RefreshTokenRepository refreshTokenRepository,
                       TokenGenerator tokenGenerator,
                       TokenSigner tokenSigner,
                       TokenExpiryPolicy tokenExpiryPolicy,
                       Clock clock) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenGenerator = tokenGenerator;
        this.tokenSigner = tokenSigner;
        this.tokenExpiryPolicy = tokenExpiryPolicy;
        this.clock = clock;
    }

    public IssuedTokens issue(UserId userId, Role role, DeviceMetadata deviceMetadata) {
        Instant now = Instant.now(clock);
        Instant accessTokenExpiresAt = tokenExpiryPolicy.accessTokenExpiry(now);
        Instant refreshTokenExpiresAt = tokenExpiryPolicy.refreshTokenExpiry(now);

        TokenGenerator.GeneratedToken generated = tokenGenerator.generate();
        RefreshToken refreshToken = RefreshToken.issue(
                RefreshTokenId.generate(), generated.tokenHash(), userId, now, refreshTokenExpiresAt, deviceMetadata);
        refreshTokenRepository.save(refreshToken);

        String accessToken = tokenSigner.sign(userId, role, now, accessTokenExpiresAt);
        log.info("Issued session {} for user {}", refreshToken.id(), userId);
        return new IssuedTokens(userId, role, accessToken, accessTokenExpiresAt,
                generated.rawValue(), refreshTokenExpiresAt);
    }
}
