package com.roadscanner.paymentservice.adapter.out.security;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Objects;

/**
 * A throwaway RS256 key pair for local development and tests, where no real {@code auth-service}
 * signing key is available. Only ever constructed when
 * {@code roadscanner.security.jwt.ephemeral-keys=true} — the structural guarantee that a private
 * key is never reachable in production (docs/services/auth-service/security-design.md).
 */
public record EphemeralJwtKeyPair(RSAPrivateKey privateKey, RSAPublicKey publicKey, String keyId) {

    public EphemeralJwtKeyPair {
        Objects.requireNonNull(privateKey, "privateKey must not be null");
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
    }

    public static EphemeralJwtKeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            return new EphemeralJwtKeyPair((RSAPrivateKey) keyPair.getPrivate(), publicKey,
                    JwtDecoderKeyMaterial.keyIdOf(publicKey));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key generation unavailable in this JVM", e);
        }
    }

    @Override
    public String toString() {
        return "EphemeralJwtKeyPair[keyId=" + keyId + "]";
    }
}
