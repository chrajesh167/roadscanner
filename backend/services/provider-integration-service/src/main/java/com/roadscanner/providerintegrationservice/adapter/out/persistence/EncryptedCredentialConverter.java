package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import com.roadscanner.providerintegrationservice.domain.port.out.CredentialCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.stereotype.Component;

/**
 * Makes credential encryption transparent: Hibernate calls this on every write and every read of
 * an annotated column, so no use case, aggregate or test ever encrypts or decrypts explicitly.
 *
 * <p>Doing it here rather than in the aggregate or the repository adapter is what makes the
 * guarantee total. There is no code path that persists a credential and forgets to encrypt it,
 * because the encryption is not something a caller performs — it is a property of the column.
 * A hand-rolled encrypt-on-save in the adapter would be one new write path away from a plaintext
 * leak.
 *
 * <p>Registered as a Spring bean so the {@link CredentialCipher} can be injected; Spring Boot
 * routes Hibernate's converter instantiation through the application context, which is what makes
 * a constructor-injected converter work at all.
 *
 * <p>{@code autoApply} is deliberately false. Applying to every {@code String} column in the
 * service would encrypt display names and provider codes too — unreadable in the database and
 * unusable in a {@code WHERE} clause. Columns opt in explicitly via {@code @Convert}.
 */
@Component
@Converter
public class EncryptedCredentialConverter implements AttributeConverter<String, String> {

    private final CredentialCipher cipher;

    public EncryptedCredentialConverter(CredentialCipher cipher) {
        this.cipher = cipher;
    }

    /** Always writes ciphertext — there is no path that stores a secret in the clear. */
    @Override
    public String convertToDatabaseColumn(String attribute) {
        return cipher.encrypt(attribute);
    }

    /**
     * Tolerates rows written before encryption existed; see {@link CredentialCipher#decrypt}. That
     * leniency is what makes enabling encryption a non-destructive change — a row stays readable
     * until something rewrites it, at which point it is stored encrypted.
     */
    @Override
    public String convertToEntityAttribute(String dbData) {
        return cipher.decrypt(dbData);
    }
}
