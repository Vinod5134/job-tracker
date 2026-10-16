package com.vinod.jobtracker.application;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "job_applications", indexes = @Index(name = "idx_app_user_status", columnList = "user_id, status"))
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 150)
    private String company;

    @Column(name = "job_role", length = 150)
    private String jobRole;

    @Column(length = 150)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_source", nullable = false, length = 20)
    private JobSource source = JobSource.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    @Column(name = "job_url", length = 1000)
    private String jobUrl;

    @Column(name = "applied_on")
    private LocalDate appliedOn;

    @Column(name = "interview_at")
    private Instant interviewAt;

    @Column(length = 50)
    private String salary;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** When the status last changed, used for "follow up after 14 days" nudges. */
    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "interview_reminded_at")
    private Instant interviewRemindedAt;

    @Column(name = "follow_up_reminded_at")
    private Instant followUpRemindedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (statusChangedAt == null) {
            statusChangedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Changes the status and resets the reminder flags that depend on it. */
    public void changeStatus(ApplicationStatus newStatus) {
        if (newStatus != status) {
            status = newStatus;
            statusChangedAt = Instant.now();
            followUpRemindedAt = null;
        }
    }

    public void changeInterviewAt(Instant newInterviewAt) {
        if (newInterviewAt == null ? interviewAt != null : !newInterviewAt.equals(interviewAt)) {
            interviewAt = newInterviewAt;
            interviewRemindedAt = null;
        }
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

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getJobRole() {
        return jobRole;
    }

    public void setJobRole(String jobRole) {
        this.jobRole = jobRole;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public JobSource getSource() {
        return source;
    }

    public void setSource(JobSource source) {
        this.source = source;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public String getJobUrl() {
        return jobUrl;
    }

    public void setJobUrl(String jobUrl) {
        this.jobUrl = jobUrl;
    }

    public LocalDate getAppliedOn() {
        return appliedOn;
    }

    public void setAppliedOn(LocalDate appliedOn) {
        this.appliedOn = appliedOn;
    }

    public Instant getInterviewAt() {
        return interviewAt;
    }

    public String getSalary() {
        return salary;
    }

    public void setSalary(String salary) {
        this.salary = salary;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }

    public Instant getInterviewRemindedAt() {
        return interviewRemindedAt;
    }

    public void setInterviewRemindedAt(Instant interviewRemindedAt) {
        this.interviewRemindedAt = interviewRemindedAt;
    }

    public Instant getFollowUpRemindedAt() {
        return followUpRemindedAt;
    }

    public void setFollowUpRemindedAt(Instant followUpRemindedAt) {
        this.followUpRemindedAt = followUpRemindedAt;
    }
}
