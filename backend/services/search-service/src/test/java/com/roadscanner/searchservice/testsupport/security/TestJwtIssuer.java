package com.roadscanner.searchservice.testsupport.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.roadscanner.searchservice.adapter.out.security.EphemeralJwtKeyPair;
import com.roadscanner.searchservice.config.SecurityConfig;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * Mints RS256 tokens signed against this test JVM's own {@link EphemeralJwtKeyPair} — present in
 * the Spring context only because {@code ephemeral-keys: true} is set for the {@code test}
 * profile. Copied from {@code booking-service}'s identical test-support class.
 *
 * <p>This is what lets the end-to-end test drive the <em>real</em> decoder and the real filter
 * chain over HTTP, rather than injecting a mock {@code Authentication} — so the token's
 * signature, issuer, expiry and role claim are all genuinely verified.
 *
 * <p>{@code role} is taken as a plain string rather than auth-service's {@code Role} enum: search
 * -service does not depend on auth-service, and duplicating the enum here would create a second
 * source of truth for the platform's role vocabulary. It also lets a test assert what happens
 * for a role this service has never heard of.
 */
public final class TestJwtIssuer {

    private final EphemeralJwtKeyPair keyPair;
    private final String issuer;

    public TestJwtIssuer(EphemeralJwtKeyPair keyPair, String issuer) {
        this.keyPair = keyPair;
        this.issuer = issuer;
    }

    public String issue(UUID subject, String role) {
        Instant now = Instant.now();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(keyPair.keyId())
                .build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject.toString())
                .issuer(issuer)
                .claim(SecurityConfig.ROLE_CLAIM, role)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .jwtID(UUID.randomUUID().toString())
                .build();

        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new RSASSASigner(keyPair.privateKey()));
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign test JWT", e);
        }
        return jwt.serialize();
    }

    public String issueAdmin() {
        return issue(UUID.randomUUID(), SecurityConfig.ADMIN_ROLE);
    }
}
