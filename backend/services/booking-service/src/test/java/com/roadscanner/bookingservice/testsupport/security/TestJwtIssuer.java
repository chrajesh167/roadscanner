package com.roadscanner.bookingservice.testsupport.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.roadscanner.bookingservice.adapter.out.security.EphemeralJwtKeyPair;
import com.roadscanner.bookingservice.config.SecurityConfig;
import com.roadscanner.bookingservice.domain.model.Role;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/** Mints RS256 tokens signed against this test JVM's own {@link EphemeralJwtKeyPair} — present
 * in the Spring context only because {@code ephemeral-keys: true} is set for the {@code test}
 * profile (config.JwtConfig), the same test-only key-material pattern
 * {@code auth-service}'s own tests use for issuing tokens they then verify within the same
 * process. */
public final class TestJwtIssuer {

    private final EphemeralJwtKeyPair keyPair;
    private final String issuer;

    public TestJwtIssuer(EphemeralJwtKeyPair keyPair, String issuer) {
        this.keyPair = keyPair;
        this.issuer = issuer;
    }

    public String issue(UUID subject, Role role) {
        Instant now = Instant.now();
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(keyPair.keyId())
                .build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject.toString())
                .issuer(issuer)
                .claim(SecurityConfig.ROLE_CLAIM, role.name())
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
}
