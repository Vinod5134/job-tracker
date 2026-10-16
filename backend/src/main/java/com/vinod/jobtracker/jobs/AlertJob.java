package com.vinod.jobtracker.jobs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * A job found in one of the user's LinkedIn / Naukri job-alert emails.
 * Only the job details are kept, never the email itself.
 */
@Entity
@Table(name = "alert_jobs",
        uniqueConstraints = @UniqueConstraint(name = "uk_alert_user_url", columnNames = {"user_id", "url_hash"}),
        indexes = @Index(name = "idx_alert_user_received", columnList = "user_id, received_at"))
public class AlertJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_source", nullable = false, length = 20)
    private JobAlertParser.Source source;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 200)
    private String company;

    @Column(length = 200)
    private String location;

    @Column(nullable = false, length = 1000)
    private String url;

    /** SHA-256 of the URL: a 1000-character column is too long for a MySQL unique index. */
    @Column(name = "url_hash", nullable = false, length = 64)
    private String urlHash;

    @Column(name = "gmail_message_id", length = 100)
    private String gmailMessageId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public JobAlertParser.Source getSource() {
        return source;
    }

    public void setSource(JobAlertParser.Source source) {
        this.source = source;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrlHash() {
        return urlHash;
    }

    public void setUrlHash(String urlHash) {
        this.urlHash = urlHash;
    }

    public String getGmailMessageId() {
        return gmailMessageId;
    }

    public void setGmailMessageId(String gmailMessageId) {
        this.gmailMessageId = gmailMessageId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
