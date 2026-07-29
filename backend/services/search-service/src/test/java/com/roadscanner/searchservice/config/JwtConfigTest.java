package com.roadscanner.searchservice.config;

import com.roadscanner.searchservice.adapter.out.security.EphemeralJwtKeyPair;
import com.roadscanner.searchservice.adapter.out.security.JwtDecoderKeyMaterial;
import com.roadscanner.searchservice.testsupport.security.TestJwtIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The verification key is the whole basis of the admin authorization added for the location
 * catalogue — if it loads wrongly, every role check downstream is meaningless. So the loading
 * rules are tested directly rather than only implicitly through a passing request.
 */
class JwtConfigTest {

    private static final String ISSUER = "roadscanner-auth-service";

    private final JwtConfig config = new JwtConfig();

    /** Mimics Spring's injection point without standing up a context. */
    private static ObjectProvider<EphemeralJwtKeyPair> provider(EphemeralJwtKeyPair keyPair) {
        return new ObjectProvider<>() {
            @Override
            public EphemeralJwtKeyPair getObject() {
                if (keyPair == null) {
                    throw new IllegalStateException("no ephemeral key pair in context");
                }
                return keyPair;
            }

            @Override
            public EphemeralJwtKeyPair getObject(Object... args) {
                return getObject();
            }

            @Override
            public EphemeralJwtKeyPair getIfAvailable() {
                return keyPair;
            }

            @Override
            public EphemeralJwtKeyPair getIfUnique() {
                return keyPair;
            }
        };
    }

    private static String pemOf(EphemeralJwtKeyPair keyPair) {
        String base64 = Base64.getMimeEncoder().encodeToString(keyPair.publicKey().getEncoded());
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }

    @Test
    void refusesToStartWhenNoKeyIsConfiguredAtAll() {
        JwtVerificationProperties properties = new JwtVerificationProperties(null, false, ISSUER);

        // Fail loud. A decoder that silently trusts nothing rejects every admin token; one that
        // silently trusts everything accepts forged ones. Neither may start quietly.
        assertThatThrownBy(() -> config.jwtDecoder(properties, provider(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("roadscanner.security.jwt.public-key-pem");
    }

    @Test
    void acceptsATokenSignedByTheConfiguredPublicKey() {
        EphemeralJwtKeyPair keyPair = EphemeralJwtKeyPair.generate();
        JwtVerificationProperties properties = new JwtVerificationProperties(pemOf(keyPair), false, ISSUER);

        JwtDecoder decoder = config.jwtDecoder(properties, provider(null));
        Jwt decoded = decoder.decode(new TestJwtIssuer(keyPair, ISSUER).issueAdmin());

        assertThat(decoded.getClaimAsString(SecurityConfig.ROLE_CLAIM)).isEqualTo(SecurityConfig.ADMIN_ROLE);
    }

    @Test
    void rejectsATokenSignedByADifferentKey() {
        JwtVerificationProperties properties =
                new JwtVerificationProperties(pemOf(EphemeralJwtKeyPair.generate()), false, ISSUER);
        JwtDecoder decoder = config.jwtDecoder(properties, provider(null));

        String forged = new TestJwtIssuer(EphemeralJwtKeyPair.generate(), ISSUER).issueAdmin();

        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsATokenFromAnUnexpectedIssuer() {
        EphemeralJwtKeyPair keyPair = EphemeralJwtKeyPair.generate();
        JwtVerificationProperties properties = new JwtVerificationProperties(pemOf(keyPair), false, ISSUER);
        JwtDecoder decoder = config.jwtDecoder(properties, provider(null));

        // Correctly signed, but not by us — a token minted for another platform must not grant
        // admin here just because the signature checks out.
        String wrongIssuer = new TestJwtIssuer(keyPair, "some-other-issuer")
                .issue(UUID.randomUUID(), SecurityConfig.ADMIN_ROLE);

        assertThatThrownBy(() -> decoder.decode(wrongIssuer)).isInstanceOf(JwtException.class);
    }

    @Test
    void ephemeralKeysProduceAUsableDecoderForLocalDevelopment() {
        EphemeralJwtKeyPair keyPair = config.ephemeralJwtKeyPair();
        JwtVerificationProperties properties = new JwtVerificationProperties(null, true, ISSUER);

        JwtDecoder decoder = config.jwtDecoder(properties, provider(keyPair));

        assertThat(decoder.decode(new TestJwtIssuer(keyPair, ISSUER).issueAdmin()).getSubject()).isNotBlank();
    }

    @Test
    void rejectsMalformedPublicKeyMaterial() {
        assertThatThrownBy(() -> JwtDecoderKeyMaterial.fromPem("-----BEGIN PUBLIC KEY-----\nnot-a-key\n-----END PUBLIC KEY-----"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not a valid RSA PEM");
    }

    @Test
    void derivesAStableKeyIdFromTheKeyItself() {
        EphemeralJwtKeyPair keyPair = EphemeralJwtKeyPair.generate();

        // The kid is a fingerprint, so the same key always names itself the same way — that is what
        // makes "which key rejected this token" answerable from a log line.
        assertThat(JwtDecoderKeyMaterial.fromPem(pemOf(keyPair)).keyId()).isEqualTo(keyPair.keyId());
        assertThat(keyPair.toString()).contains(keyPair.keyId()).doesNotContain("private");
    }

    @Test
    void propertiesRequireAnIssuer() {
        assertThatThrownBy(() -> new JwtVerificationProperties(null, true, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    void blankPemCountsAsNoConfiguredKey() {
        assertThat(new JwtVerificationProperties("   ", false, ISSUER).hasConfiguredPublicKey()).isFalse();
        assertThat(new JwtVerificationProperties(null, false, ISSUER).hasConfiguredPublicKey()).isFalse();
    }
}
