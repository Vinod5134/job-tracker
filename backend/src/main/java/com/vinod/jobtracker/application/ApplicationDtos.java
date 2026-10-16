package com.vinod.jobtracker.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;

public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    public record ApplicationRequest(
            @NotBlank(message = "Enter the company name") @Size(max = 150) String company,
            @Size(max = 150) String jobRole,
            @Size(max = 150) String location,
            JobSource source,
            ApplicationStatus status,
            // Only http(s) links, so nothing like "javascript:" ends up rendered as a link
            @Size(max = 1000) @Pattern(regexp = "^$|^https?://.+", message = "Use a link starting with http:// or https://")
            String jobUrl,
            LocalDate appliedOn,
            Instant interviewAt,
            @Size(max = 50) String salary,
            @Size(max = 2000) String notes) {
    }

    public record StatusRequest(@NotNull ApplicationStatus status) {
    }

    /** What an email changed, so it can be undone: the card, its status before, and whether it was new. */
    public record EmailUpdate(ApplicationResponse application, ApplicationStatus previousStatus, boolean created) {
    }

    public record ApplicationResponse(
            Long id, String company, String jobRole, String location, JobSource source,
            ApplicationStatus status, String jobUrl, LocalDate appliedOn, Instant interviewAt,
            String salary, String notes, Instant createdAt, Instant updatedAt, Instant statusChangedAt) {

        public static ApplicationResponse from(JobApplication a) {
            return new ApplicationResponse(a.getId(), a.getCompany(), a.getJobRole(), a.getLocation(),
                    a.getSource(), a.getStatus(), a.getJobUrl(), a.getAppliedOn(), a.getInterviewAt(),
                    a.getSalary(), a.getNotes(), a.getCreatedAt(), a.getUpdatedAt(), a.getStatusChangedAt());
        }
    }
}
