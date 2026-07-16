package com.roadscanner.authservice.adapter.out.security;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.roadscanner.authservice.domain.model.Role;
import com.roadscanner.authservice.domain.model.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The signing round-trip from testing-strategy.md: sign, verify with the public key, and —
 * the explicitly non-optional scenario — reject a tampered token (valid structure, invalid
 * signature).
 */
class JwtTokenSignerAdapterTest {

    private static final Instant ISSUED_AT = Instant.parse("2026-07-14T10:00:00Z").truncatedTo(ChronoUnit.SECONDS);
    private static final Instant EXPIRES_AT = ISSUED_AT.plusSeconds(900);

    private final JwtKeyMaterial keyMaterial = JwtKeyMaterial.ephemeral();
    private final JwtTokenSignerAdapter signer = new JwtTokenSignerAdapter(keyMaterial, "roadscanner-auth-service");
    private final UserId userId = UserId.generate();

    @Test
    void signedTokenVerifiesWithThePublicKeyAndCarriesExactlyTheDesignedClaims() throws Exception {
        String token = signer.sign(userId, Role.OPERATOR, ISSUED_AT, EXPIRES_AT);

        SignedJWT jwt = SignedJWT.parse(token);
        assertThat(jwt.verify(new RSASSAVerifier(keyMaterial.publicKey()))).isTrue();
        assertThat(jwt.getHeader().getAlgorithm().getName()).isEqualTo("RS256");
        assertThat(jwt.getHeader().getKeyID()).isEqualTo(keyMaterial.keyId());
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(userId.toString());
        assertThat(jwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo("OPERATOR");
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("roadscanner-auth-service");
        assertThat(jwt.getJWTClaimsSet().getIssueTime()).isEqualTo(Date.from(ISSUED_AT));
        assertThat(jwt.getJWTClaimsSet().getExpirationTime()).isEqualTo(Date.from(EXPIRES_AT));
        // No profile data ever rides in the token — security-design.md.
        assertThat(jwt.getJWTClaimsSet().getClaims().keySet())
                .containsOnly("sub", "iss", "role", "iat", "exp", "jti");
    }

    @Test
    void tamperedPayloadFailsSignatureVerification() throws Exception {
        String token = signer.sign(userId, Role.TRAVELER, ISSUED_AT, EXPIRES_AT);
        String forged = forgeRoleClaim(token);

        SignedJWT tampered = SignedJWT.parse(forged);
        assertThat(tampered.getJWTClaimsSet().getStringClaim("role")).isEqualTo("ADMIN");
        assertThat(tampered.verify(new RSASSAVerifier(keyMaterial.publicKey()))).isFalse();
    }

    @Test
    void tokenSignedWithADifferentKeyFailsVerification() throws Exception {
        JwtTokenSignerAdapter rogueSigner = new JwtTokenSignerAdapter(
                JwtKeyMaterial.ephemeral(), "roadscanner-auth-service");
        String rogueToken = rogueSigner.sign(userId, Role.ADMIN, ISSUED_AT, EXPIRES_AT);

        assertThat(SignedJWT.parse(rogueToken).verify(new RSASSAVerifier(keyMaterial.publicKey()))).isFalse();
    }

    /** Keeps the structure valid (header.payload.signature) while privilege-escalating the payload. */
    private String forgeRoleClaim(String token) {
        String[] parts = token.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]))
                .replace("TRAVELER", "ADMIN");
        parts[1] = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes());
        return String.join(".", parts);
    }
}
