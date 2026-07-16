package com.roadscanner.authservice.adapter.out.security;

import com.roadscanner.authservice.domain.model.PasswordHash;
import com.roadscanner.authservice.domain.port.out.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Objects;

/**
 * BCrypt implementation of the {@link PasswordHasher} port — the adaptive, deliberately-slow
 * algorithm docs/services/auth-service/security-design.md requires for passwords. The cost
 * factor (strength) is externalized configuration, not a constant, because it is "an
 * operational tuning knob, expected to increase over time as hardware improves" (same doc).
 *
 * The {@code algorithmId} encodes both the algorithm and the strength (e.g. {@code bcrypt-12})
 * so PasswordHashingPolicy can detect hashes created under an older baseline and trigger the
 * rehash-on-login upgrade. {@link #matches} works across strengths regardless — BCrypt encodes
 * its cost factor inside the hash itself.
 */
public class BCryptPasswordHasherAdapter implements PasswordHasher {

    private final BCryptPasswordEncoder encoder;
    private final String algorithmId;

    public BCryptPasswordHasherAdapter(int strength) {
        this.encoder = new BCryptPasswordEncoder(strength);
        this.algorithmId = "bcrypt-" + strength;
    }

    @Override
    public PasswordHash hash(String rawPassword) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        return new PasswordHash(encoder.encode(rawPassword), algorithmId);
    }

    @Override
    public boolean matches(String rawPassword, PasswordHash hash) {
        Objects.requireNonNull(rawPassword, "rawPassword must not be null");
        Objects.requireNonNull(hash, "hash must not be null");
        return encoder.matches(rawPassword, hash.value());
    }

    public String algorithmId() {
        return algorithmId;
    }
}
