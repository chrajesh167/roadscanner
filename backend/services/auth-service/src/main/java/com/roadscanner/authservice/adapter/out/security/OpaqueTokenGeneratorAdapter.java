package com.roadscanner.authservice.adapter.out.security;

import com.roadscanner.authservice.domain.model.TokenHash;
import com.roadscanner.authservice.domain.port.out.TokenGenerator;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Implements the {@link TokenGenerator} port: 256-bit CSPRNG raw tokens, SHA-256 stored
 * hashes. SHA-256 without salt or work factor is deliberate here — unlike a password, a
 * 256-bit random token cannot be brute-forced or dictionary-attacked, and the lookup path
 * (find-by-token-hash) requires recomputing the identical hash from the presented raw value,
 * which a salted adaptive hash would make impossible. See the port's Javadoc.
 */
@Component
class OpaqueTokenGeneratorAdapter implements TokenGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public GeneratedToken generate() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String rawValue = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new GeneratedToken(rawValue, hashOf(rawValue));
    }

    @Override
    public TokenHash hashOf(String rawToken) {
        Objects.requireNonNull(rawToken, "rawToken must not be null");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return new TokenHash(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable in this JVM", e);
        }
    }
}
