package com.vinod.jobtracker.gmail;

import com.vinod.jobtracker.application.ApplicationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * A job-related email that was detected during Gmail sync.
 * Only the sender, subject and what we detected are stored, never the email body.
 */
@Entity
@Table(name = "email_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_email_user_msg", columnNames = {"user_id", "gmail_message_id"}))
public class EmailRecord {

    public enum State {
        /** High confidence: the board was updated automatically. */
        AUTO_APPLIED,
        /** High confidence, but the board already had that status (or a later one). */
        NO_CHANGE,
        /** Low confidence: waiting for the user to confirm. */
        NEEDS_REVIEW,
        ACCEPTED,
        DISMISSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "gmail_message_id", nullable = false, length = 100)
    private String gmailMessageId;

    @Column(name = "sender", length = 300)
    private String from;

    @Column(length = 500)
    private String subject;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "detected_status", length = 20)
    private ApplicationStatus detectedStatus;

    @Column(name = "detected_company", length = 150)
    private String detectedCompany;

    @Column(name = "detected_role", length = 150)
    private String detectedRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private State state;

    @Column(name = "application_id")
    private Long applicationId;

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

    public String getGmailMessageId() {
        return gmailMessageId;
    }

    public void setGmailMessageId(String gmailMessageId) {
        this.gmailMessageId = gmailMessageId;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = cut(from, 300);
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = cut(subject, 500);
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }

    public ApplicationStatus getDetectedStatus() {
        return detectedStatus;
    }

    public void setDetectedStatus(ApplicationStatus detectedStatus) {
        this.detectedStatus = detectedStatus;
    }

    public String getDetectedCompany() {
        return detectedCompany;
    }

    public void setDetectedCompany(String detectedCompany) {
        this.detectedCompany = cut(detectedCompany, 150);
    }

    public String getDetectedRole() {
        return detectedRole;
    }

    public void setDetectedRole(String detectedRole) {
        this.detectedRole = cut(detectedRole, 150);
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
