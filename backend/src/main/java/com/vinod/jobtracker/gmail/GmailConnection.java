package com.vinod.jobtracker.gmail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A user's Gmail link. The refresh token is stored AES-encrypted.
 */
@Entity
@Table(name = "gmail_connections")
public class GmailConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "google_email", length = 254)
    private String googleEmail;

    @Column(name = "refresh_token_enc", nullable = false, length = 2000)
    private String refreshTokenEncrypted;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getGoogleEmail() {
        return googleEmail;
    }

    public void setGoogleEmail(String googleEmail) {
        this.googleEmail = googleEmail;
    }

    public String getRefreshTokenEncrypted() {
        return refreshTokenEncrypted;
    }

    public void setRefreshTokenEncrypted(String refreshTokenEncrypted) {
        this.refreshTokenEncrypted = refreshTokenEncrypted;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(Instant connectedAt) {
        this.connectedAt = connectedAt;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(Instant lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError == null || lastError.length() <= 500 ? lastError : lastError.substring(0, 500);
    }
}
