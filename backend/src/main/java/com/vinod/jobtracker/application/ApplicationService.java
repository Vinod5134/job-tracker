package com.vinod.jobtracker.application;

import com.vinod.jobtracker.activity.Activity;
import com.vinod.jobtracker.activity.ActivityService;
import com.vinod.jobtracker.application.ApplicationDtos.ApplicationRequest;
import com.vinod.jobtracker.application.ApplicationDtos.ApplicationResponse;
import com.vinod.jobtracker.application.ApplicationDtos.EmailUpdate;
import com.vinod.jobtracker.common.NotFoundException;
import com.vinod.jobtracker.realtime.LiveUpdateService;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private final JobApplicationRepository applications;
    private final ActivityService activityService;
    private final LiveUpdateService live;

    public ApplicationService(JobApplicationRepository applications, ActivityService activityService,
                              LiveUpdateService live) {
        this.applications = applications;
        this.activityService = activityService;
        this.live = live;
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> list(Long userId, ApplicationStatus status, String search) {
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return applications.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .filter(a -> status == null || a.getStatus() == status)
                .filter(a -> q.isEmpty() || contains(a.getCompany(), q) || contains(a.getJobRole(), q)
                        || contains(a.getLocation(), q))
                .map(ApplicationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(Long userId, Long id) {
        return ApplicationResponse.from(find(userId, id));
    }

    @Transactional
    public ApplicationResponse create(Long userId, ApplicationRequest request) {
        JobApplication app = new JobApplication();
        app.setUserId(userId);
        apply(app, request);
        if (app.getAppliedOn() == null && app.getStatus() != ApplicationStatus.SAVED) {
            app.setAppliedOn(LocalDate.now());
        }
        JobApplication saved = applications.save(app);
        ApplicationResponse response = ApplicationResponse.from(saved);
        live.send(userId, LiveUpdateService.APPLICATION_CREATED, response);
        activityService.log(userId, saved.getId(), "Added " + describe(saved) + " to " + saved.getStatus().label(),
                Activity.Origin.MANUAL);
        return response;
    }

    @Transactional
    public ApplicationResponse update(Long userId, Long id, ApplicationRequest request) {
        JobApplication app = find(userId, id);
        ApplicationStatus before = app.getStatus();
        apply(app, request);
        JobApplication saved = applications.saveAndFlush(app);
        ApplicationResponse response = ApplicationResponse.from(saved);
        live.send(userId, LiveUpdateService.APPLICATION_UPDATED, response);
        String message = before != saved.getStatus()
                ? "Moved " + saved.getCompany() + " to " + saved.getStatus().label()
                : "Updated " + describe(saved);
        activityService.log(userId, saved.getId(), message, Activity.Origin.MANUAL);
        return response;
    }

    /** Used by drag-and-drop on the board. */
    @Transactional
    public ApplicationResponse changeStatus(Long userId, Long id, ApplicationStatus status) {
        JobApplication app = find(userId, id);
        if (app.getStatus() == status) {
            return ApplicationResponse.from(app);
        }
        app.changeStatus(status);
        if (status != ApplicationStatus.SAVED && app.getAppliedOn() == null) {
            app.setAppliedOn(LocalDate.now());
        }
        JobApplication saved = applications.saveAndFlush(app);
        ApplicationResponse response = ApplicationResponse.from(saved);
        live.send(userId, LiveUpdateService.APPLICATION_UPDATED, response);
        activityService.log(userId, saved.getId(), "Moved " + saved.getCompany() + " to " + status.label(),
                Activity.Origin.MANUAL);
        return response;
    }

    @Transactional
    public void delete(Long userId, Long id) {
        JobApplication app = find(userId, id);
        applications.delete(app);
        live.send(userId, LiveUpdateService.APPLICATION_DELETED, Map.of("id", id));
        activityService.log(userId, null, "Removed " + describe(app), Activity.Origin.MANUAL);
    }

    /**
     * Applies a status found in an email.
     * Matches an existing card by company name, or creates a new one.
     *
     * @param manual true when the user confirmed it in "Needs review" (skips the forward-only rule)
     */
    @Transactional
    public Optional<EmailUpdate> applyEmailUpdate(Long userId, String company, String role,
                                                  ApplicationStatus status, boolean manual) {
        Optional<JobApplication> existing = applications.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .filter(a -> CompanyNames.sameCompany(a.getCompany(), company))
                .findFirst();

        if (existing.isPresent()) {
            JobApplication app = existing.get();
            boolean allowed = manual ? app.getStatus() != status : app.getStatus().canAutoMoveTo(status);
            if (!allowed) {
                return Optional.empty();
            }
            ApplicationStatus previous = app.getStatus();
            app.changeStatus(status);
            if ((app.getJobRole() == null || app.getJobRole().isBlank()) && role != null) {
                app.setJobRole(truncate(role, 150));
            }
            JobApplication saved = applications.saveAndFlush(app);
            ApplicationResponse response = ApplicationResponse.from(saved);
            live.send(userId, LiveUpdateService.APPLICATION_UPDATED, response);
            activityService.log(userId, saved.getId(), "Email: " + saved.getCompany() + " moved to " + status.label(),
                    Activity.Origin.EMAIL);
            return Optional.of(new EmailUpdate(response, previous, false));
        }

        JobApplication app = new JobApplication();
        app.setUserId(userId);
        app.setCompany(truncate(company, 150));
        app.setJobRole(role == null ? null : truncate(role, 150));
        app.setSource(JobSource.EMAIL);
        app.changeStatus(status);
        app.setAppliedOn(LocalDate.now());
        JobApplication saved = applications.save(app);
        ApplicationResponse response = ApplicationResponse.from(saved);
        live.send(userId, LiveUpdateService.APPLICATION_CREATED, response);
        activityService.log(userId, saved.getId(), "Email: added " + describe(saved) + " as " + status.label(),
                Activity.Origin.EMAIL);
        return Optional.of(new EmailUpdate(response, null, true));
    }

    /**
     * Undoes a board change that came from an email: removes the card if the email created it,
     * otherwise puts the status back (only if nobody has changed it since).
     *
     * @param createdCard null for records saved before this was tracked; then a card whose source is
     *                    EMAIL is treated as created by the email
     */
    @Transactional
    public void revertEmailUpdate(Long userId, Long applicationId, Boolean createdCard,
                                  ApplicationStatus previousStatus, ApplicationStatus detectedStatus) {
        Optional<JobApplication> found = applications.findByIdAndUserId(applicationId, userId);
        if (found.isEmpty()) {
            return;
        }
        JobApplication app = found.get();
        boolean created = createdCard != null ? createdCard : app.getSource() == JobSource.EMAIL;
        if (created) {
            delete(userId, applicationId);
        } else if (previousStatus != null && app.getStatus() == detectedStatus) {
            changeStatus(userId, applicationId, previousStatus);
        }
    }

    private JobApplication find(Long userId, Long id) {
        // Always scoped by user id, so one user can never read or change another user's data
        return applications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Application not found"));
    }

    private void apply(JobApplication app, ApplicationRequest r) {
        app.setCompany(r.company().trim());
        app.setJobRole(blankToNull(r.jobRole()));
        app.setLocation(blankToNull(r.location()));
        app.setSource(r.source() == null ? JobSource.OTHER : r.source());
        app.changeStatus(r.status() == null ? ApplicationStatus.APPLIED : r.status());
        app.setJobUrl(blankToNull(r.jobUrl()));
        app.setAppliedOn(r.appliedOn());
        app.changeInterviewAt(r.interviewAt());
        app.setSalary(blankToNull(r.salary()));
        app.setNotes(blankToNull(r.notes()));
    }

    private static String describe(JobApplication a) {
        return a.getJobRole() == null ? a.getCompany() : a.getCompany() + " (" + a.getJobRole() + ")";
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
