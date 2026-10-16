package com.vinod.jobtracker.reminder;

import com.vinod.jobtracker.activity.Activity;
import com.vinod.jobtracker.activity.ActivityService;
import com.vinod.jobtracker.application.ApplicationStatus;
import com.vinod.jobtracker.application.JobApplication;
import com.vinod.jobtracker.application.JobApplicationRepository;
import com.vinod.jobtracker.realtime.LiveUpdateService;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Background reminders, pushed live to the user's open browsers and kept in the activity feed:
 * - interviews in the next 24 hours
 * - applications with no reply for 14 days ("time to follow up")
 */
@Component
public class ReminderScheduler {

    private static final int FOLLOW_UP_DAYS = 14;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")
            .withZone(ZoneId.of("Asia/Kolkata"));

    private final JobApplicationRepository applications;
    private final ActivityService activityService;
    private final LiveUpdateService live;

    public ReminderScheduler(JobApplicationRepository applications, ActivityService activityService,
                             LiveUpdateService live) {
        this.applications = applications;
        this.activityService = activityService;
        this.live = live;
    }

    @Scheduled(fixedDelay = 15, initialDelay = 1, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    @Transactional
    public void sendReminders() {
        Instant now = Instant.now();

        for (JobApplication app : applications.findByStatusAndInterviewAtBetweenAndInterviewRemindedAtIsNull(
                ApplicationStatus.INTERVIEW, now, now.plus(24, ChronoUnit.HOURS))) {
            String when = TIME.format(app.getInterviewAt());
            live.notify(app.getUserId(), "Interview coming up", app.getCompany() + " on " + when, "info");
            activityService.log(app.getUserId(), app.getId(), "Reminder: " + app.getCompany() + " interview on " + when,
                    Activity.Origin.SYSTEM);
            app.setInterviewRemindedAt(now);
        }

        for (JobApplication app : applications.findByStatusAndStatusChangedAtBeforeAndFollowUpRemindedAtIsNull(
                ApplicationStatus.APPLIED, now.minus(FOLLOW_UP_DAYS, ChronoUnit.DAYS))) {
            live.notify(app.getUserId(), "Time to follow up",
                    "No reply from " + app.getCompany() + " in " + FOLLOW_UP_DAYS + " days", "warning");
            activityService.log(app.getUserId(), app.getId(), "Follow up with " + app.getCompany() + "?",
                    Activity.Origin.SYSTEM);
            app.setFollowUpRemindedAt(now);
        }
    }
}
