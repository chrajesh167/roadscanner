package com.roadscanner.providerintegrationservice.testsupport;

import com.roadscanner.providerintegrationservice.adapter.out.security.AesGcmCredentialCipher;
import com.roadscanner.providerintegrationservice.domain.port.out.CredentialCipher;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Supplies the {@link CredentialCipher} that {@code EncryptedCredentialConverter} requires.
 *
 * <p>Every {@code @DataJpaTest} slice needs this, not just the ones that touch credentials: the
 * converter is attached to a column on {@code ProviderCredentialsJpaEntity}, and Hibernate builds
 * the entire entity manager — including that entity — whatever the slice under test is actually
 * exercising.
 *
 * <p>A fixed key rather than the real {@code CredentialEncryptionConfig}, on purpose. Slice tests
 * do not activate the {@code test} profile, so the production config would hit its fail-loud
 * "no key configured" path — which is correct behaviour, and not what these tests are about. A
 * deterministic key also keeps ciphertext stable within a run, so a test can decrypt what it
 * wrote. The real key-loading rules are covered by {@code AesGcmCredentialCipherTest} and
 * end-to-end by the {@code test} profile's ephemeral key.
 */
@TestConfiguration
public class CredentialEncryptionTestConfig {

    /** 32 bytes: a valid AES-256 key, and obviously a test fixture rather than a real secret. */
    private static final byte[] TEST_KEY = "roadscanner-test-key-0123456789!".getBytes();

    @Bean
    public CredentialCipher credentialCipher() {
        return new AesGcmCredentialCipher(TEST_KEY);
    }
}
