package com.roadscanner.authservice.adapter.out.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.model.UserId;
import com.roadscanner.authservice.domain.port.out.TokenSigner;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

/**
 * RS256 implementation of the {@link TokenSigner} port, per
 * docs/services/auth-service/security-design.md: asymmetric signing so only this service can
 * issue tokens while every other service verifies with the public key alone; {@code kid} in
 * the header for rotation-window support; claims limited to subject, role, issued-at, expiry
 * (plus issuer and a unique {@code jti}) — never profile data.
 */
public class JwtTokenSignerAdapter implements TokenSigner {

    public static final String ROLE_CLAIM = "role";

    private final JwtKeyMaterial keyMaterial;
    private final String issuer;

    public JwtTokenSignerAdapter(JwtKeyMaterial keyMaterial, String issuer) {
        this.keyMaterial = Objects.requireNonNull(keyMaterial, "keyMaterial must not be null");
        this.issuer = Objects.requireNonNull(issuer, "issuer must not be null");
    }

    @Override
    public String sign(UserId userId, Role role, Instant issuedAt, Instant expiresAt) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(keyMaterial.keyId())
                .build();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(userId.value().toString())
                .issuer(issuer)
                .claim(ROLE_CLAIM, role.name())
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .jwtID(UUID.randomUUID().toString())
                .build();

        SignedJWT jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new RSASSASigner(keyMaterial.privateKey()));
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign access token", e);
        }
        return jwt.serialize();
    }
}
