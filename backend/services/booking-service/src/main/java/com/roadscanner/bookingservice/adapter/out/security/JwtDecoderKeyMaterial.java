package com.roadscanner.bookingservice.adapter.out.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * The RS256 public key this service verifies {@code auth-service}-issued tokens against —
 * deliberately public-key-only, structurally incapable of holding a private key, matching
 * {@code auth-service}'s own security-design rationale: <em>"compromising a downstream service
 * only exposes its ability to verify, never to issue"</em>
 * (docs/services/auth-service/security-design.md). Production key material arrives as PEM via
 * configuration sourced from a secrets manager, mirroring {@code auth-service}'s own
 * {@code JwtKeyMaterial} loading — see {@code config.JwtConfig} for the fail-loud loading rule
 * and the ephemeral local/test alternative ({@link EphemeralJwtKeyPair}).
 */
public record JwtDecoderKeyMaterial(RSAPublicKey publicKey, String keyId) {

    public JwtDecoderKeyMaterial {
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
    }

    public static JwtDecoderKeyMaterial fromPem(String publicKeyPem) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                    new X509EncodedKeySpec(decodePem(publicKeyPem)));
            return new JwtDecoderKeyMaterial(publicKey, keyIdOf(publicKey));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Configured JWT public key is not a valid RSA PEM (X.509 public key)", e);
        }
    }

    static byte[] decodePem(String pem) {
        String base64 = pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64.getBytes(StandardCharsets.US_ASCII));
    }

    static String keyIdOf(RSAPublicKey publicKey) {
        try {
            byte[] fingerprint = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
            return HexFormat.of().formatHex(fingerprint, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable in this JVM", e);
        }
    }

    @Override
    public String toString() {
        return "JwtDecoderKeyMaterial[keyId=" + keyId + "]";
    }
}
