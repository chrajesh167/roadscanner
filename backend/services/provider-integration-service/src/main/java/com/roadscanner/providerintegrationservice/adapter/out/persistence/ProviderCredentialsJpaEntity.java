package com.roadscanner.providerintegrationservice.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistence shape for
 * {@link com.roadscanner.providerintegrationservice.domain.model.ProviderCredentials}.
 *
 * <p>{@code providerId} is a plain UUID column rather than a {@code @ManyToOne}: the foreign key
 * still exists in V6, but an association here would let a caller traverse from credentials into a
 * provider entity and back, and would drag a join into every credential read. Same reasoning as
 * search-service's provider mapping entity.
 *
 * <p>Deliberately has no {@code toString()} — the inherited identity one cannot print a secret,
 * whereas a generated or hand-written one eventually would.
 */
@Entity
@Table(name = "provider_credentials")
public class ProviderCredentialsJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "provider_id", nullable = false, updatable = false)
    private UUID providerId;

    @Column(name = "partner_email")
    private String partnerEmail;

    @Column(name = "partner_password")
    private String partnerPassword;

    @Column(name = "partner_token")
    private String partnerToken;

    @Column(name = "encrypted", nullable = false)
    private boolean encrypted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProviderCredentialsJpaEntity() {
        // Required by JPA.
    }

    ProviderCredentialsJpaEntity(UUID id, UUID providerId, String partnerEmail, String partnerPassword,
                                 String partnerToken, boolean encrypted, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.providerId = providerId;
        this.partnerEmail = partnerEmail;
        this.partnerPassword = partnerPassword;
        this.partnerToken = partnerToken;
        this.encrypted = encrypted;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    UUID getId() {
        return id;
    }

    UUID getProviderId() {
        return providerId;
    }

    String getPartnerEmail() {
        return partnerEmail;
    }

    void setPartnerEmail(String partnerEmail) {
        this.partnerEmail = partnerEmail;
    }

    String getPartnerPassword() {
        return partnerPassword;
    }

    void setPartnerPassword(String partnerPassword) {
        this.partnerPassword = partnerPassword;
    }

    String getPartnerToken() {
        return partnerToken;
    }

    void setPartnerToken(String partnerToken) {
        this.partnerToken = partnerToken;
    }

    boolean isEncrypted() {
        return encrypted;
    }

    void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
