package com.vinod.jobtracker.dashboard;

import com.vinod.jobtracker.activity.ActivityService;
import com.vinod.jobtracker.activity.ActivityService.ActivityResponse;
import com.vinod.jobtracker.application.ApplicationDtos.ApplicationResponse;
import com.vinod.jobtracker.application.ApplicationStatus;
import com.vinod.jobtracker.application.JobApplication;
import com.vinod.jobtracker.application.JobApplicationRepository;
import com.vinod.jobtracker.config.CurrentUser;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    static final int FOLLOW_UP_DAYS = 14;
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final JobApplicationRepository applications;
    private final ActivityService activityService;

    public DashboardController(JobApplicationRepository applications, ActivityService activityService) {
        this.applications = applications;
        this.activityService = activityService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public DashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt) {
        Long userId = CurrentUser.id(jwt);
        List<JobApplication> all = applications.findByUserIdOrderByUpdatedAtDesc(userId);
        Instant now = Instant.now();

        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus s : ApplicationStatus.values()) {
            counts.put(s, 0L);
        }
        all.forEach(a -> counts.merge(a.getStatus(), 1L, Long::sum));

        long applied = all.size() - counts.get(ApplicationStatus.SAVED);
        long responses = counts.get(ApplicationStatus.INTERVIEW) + counts.get(ApplicationStatus.OFFER)
                + counts.get(ApplicationStatus.REJECTED);
        int responseRate = applied == 0 ? 0 : (int) Math.round(responses * 100.0 / applied);

        List<ApplicationResponse> upcoming = all.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.INTERVIEW && a.getInterviewAt() != null
                        && a.getInterviewAt().isAfter(now))
                .sorted(Comparator.comparing(JobApplication::getInterviewAt))
                .limit(5)
                .map(ApplicationResponse::from)
                .toList();

        Instant followUpCutoff = now.minus(FOLLOW_UP_DAYS, ChronoUnit.DAYS);
        List<ApplicationResponse> followUps = all.stream()
                .filter(a -> a.getStatus() == ApplicationStatus.APPLIED && a.getStatusChangedAt().isBefore(followUpCutoff))
                .sorted(Comparator.comparing(JobApplication::getStatusChangedAt))
                .limit(5)
                .map(ApplicationResponse::from)
                .toList();

        return new DashboardResponse(all.size(), counts, responseRate, upcoming, followUps,
                weekly(all), activityService.recent(userId));
    }

    /** Applications per week for the last 8 weeks (weeks start on Monday). */
    private List<WeekCount> weekly(List<JobApplication> all) {
        LocalDate thisWeek = LocalDate.now(ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<WeekCount> weeks = new ArrayList<>();
        for (int i = 7; i >= 0; i--) {
            LocalDate start = thisWeek.minusWeeks(i);
            LocalDate end = start.plusDays(7);
            long count = all.stream()
                    .filter(a -> a.getStatus() != ApplicationStatus.SAVED)
                    .map(a -> a.getAppliedOn() != null ? a.getAppliedOn() : LocalDate.ofInstant(a.getCreatedAt(), ZONE))
                    .filter(d -> !d.isBefore(start) && d.isBefore(end))
                    .count();
            weeks.add(new WeekCount(start, count));
        }
        return weeks;
    }

    public record WeekCount(LocalDate weekStart, long count) {
    }

    public record DashboardResponse(int total, Map<ApplicationStatus, Long> counts, int responseRate,
                                    List<ApplicationResponse> upcomingInterviews,
                                    List<ApplicationResponse> followUps,
                                    List<WeekCount> weekly,
                                    List<ActivityResponse> recentActivity) {
    }
}
