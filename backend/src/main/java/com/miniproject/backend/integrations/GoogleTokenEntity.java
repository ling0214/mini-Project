package com.miniproject.backend.integrations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * This is a single-operator local tool (no user accounts/sessions yet), so
 * there is exactly one Google connection, not one per user — a fixed-id
 * singleton row rather than a real per-user token table. If multi-user
 * support ever lands, this becomes keyed by user id instead.
 */
@Entity
@Table(name = "google_tokens")
public class GoogleTokenEntity {

    static final String SINGLETON_ID = "default";

    @Id
    @Column(length = 36)
    private String id = SINGLETON_ID;

    @Lob
    @Column(name = "access_token", nullable = false)
    private String accessToken;

    @Lob
    @Column(name = "refresh_token")
    private String refreshToken;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private String scope;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GoogleTokenEntity() {
        // JPA
    }

    GoogleTokenEntity(String accessToken, String refreshToken, Instant expiresAt, String scope) {
        this.id = SINGLETON_ID;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = expiresAt;
        this.scope = scope;
        this.updatedAt = Instant.now();
    }

    void update(String accessToken, String refreshToken, Instant expiresAt, String scope) {
        this.accessToken = accessToken;
        if (refreshToken != null && !refreshToken.isBlank()) {
            // Google only returns a refresh_token on the very first consent
            // (or when prompt=consent forces re-issue) — keep the existing
            // one on subsequent access-token-only refreshes.
            this.refreshToken = refreshToken;
        }
        this.expiresAt = expiresAt;
        this.scope = scope;
        this.updatedAt = Instant.now();
    }

    String getId() {
        return id;
    }

    String getAccessToken() {
        return accessToken;
    }

    String getRefreshToken() {
        return refreshToken;
    }

    Instant getExpiresAt() {
        return expiresAt;
    }

    String getScope() {
        return scope;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
