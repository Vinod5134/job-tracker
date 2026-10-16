package com.vinod.jobtracker.activity;

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
import java.time.Instant;

/**
 * One line in the "Live activity" feed, e.g. "Moved TCS to Interview".
 */
@Entity
@Table(name = "activities", indexes = @Index(name = "idx_activity_user", columnList = "user_id, created_at"))
public class Activity {

    public enum Origin { MANUAL, EMAIL, SYSTEM }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "application_id")
    private Long applicationId;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Origin origin;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Activity() {
    }

    public Activity(Long userId, Long applicationId, String message, Origin origin) {
        this.userId = userId;
        this.applicationId = applicationId;
        this.message = message.length() > 500 ? message.substring(0, 500) : message;
        this.origin = origin;
    }

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

    public Long getApplicationId() {
        return applicationId;
    }

    public String getMessage() {
        return message;
    }

    public Origin getOrigin() {
        return origin;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
