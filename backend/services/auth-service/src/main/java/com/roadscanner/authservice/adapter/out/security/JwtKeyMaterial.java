package com.roadscanner.authservice.adapter.out.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * The RS256 signing key pair plus its stable key identifier ({@code kid}), per
 * docs/services/auth-service/security-design.md: the kid is embedded in every token header so
 * more than one public key can be valid at once during a rotation window. The kid is derived
 * from the public key's fingerprint, so the same key always produces the same identifier —
 * no separate kid bookkeeping to drift out of sync with the key itself.
 *
 * Production key material arrives as PEM via configuration sourced from a secrets manager
 * (never checked into source — see config.JwtConfig for the fail-loud loading rule);
 * {@link #ephemeral()} exists only for local/test profiles where a throwaway pair is
 * explicitly opted into.
 */
public record JwtKeyMaterial(RSAPrivateKey privateKey, RSAPublicKey publicKey, String keyId) {

    public JwtKeyMaterial {
        Objects.requireNonNull(privateKey, "privateKey must not be null");
        Objects.requireNonNull(publicKey, "publicKey must not be null");
        Objects.requireNonNull(keyId, "keyId must not be null");
    }

    public static JwtKeyMaterial fromPem(String privateKeyPem, String publicKeyPem) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(decodePem(privateKeyPem)));
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(
                    new X509EncodedKeySpec(decodePem(publicKeyPem)));
            return new JwtKeyMaterial(privateKey, publicKey, keyIdOf(publicKey));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Configured JWT key material is not a valid RSA PEM pair (PKCS#8 private / X.509 public)", e);
        }
    }

    public static JwtKeyMaterial ephemeral() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
            return new JwtKeyMaterial((RSAPrivateKey) keyPair.getPrivate(), publicKey, keyIdOf(publicKey));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA key generation unavailable in this JVM", e);
        }
    }

    private static byte[] decodePem(String pem) {
        String base64 = pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64.getBytes(StandardCharsets.US_ASCII));
    }

    private static String keyIdOf(RSAPublicKey publicKey) {
        try {
            byte[] fingerprint = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
            return HexFormat.of().formatHex(fingerprint, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable in this JVM", e);
        }
    }

    @Override
    public String toString() {
        return "JwtKeyMaterial[keyId=" + keyId + "]";
    }
}
