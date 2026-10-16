package com.vinod.jobtracker.jobs;

import com.vinod.jobtracker.config.CurrentUser;
import com.vinod.jobtracker.gmail.GmailSyncService;
import com.vinod.jobtracker.jobs.JobAlertService.AlertSourceStatus;
import org.springframework.web.client.RestClientException;
import com.vinod.jobtracker.jobs.JobFeedService.JobResult;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobFeedService feed;
    private final JobAlertService alerts;
    private final GmailSyncService gmailSync;

    public JobController(JobFeedService feed, JobAlertService alerts, GmailSyncService gmailSync) {
        this.feed = feed;
        this.alerts = alerts;
        this.gmailSync = gmailSync;
    }

    /** Which of LinkedIn / Naukri / Indeed are sending job alerts to the connected Gmail. */
    @GetMapping("/alert-status")
    public List<AlertSourceStatus> alertStatus(@AuthenticationPrincipal Jwt jwt) {
        Long userId = CurrentUser.id(jwt);
        return gmailSync.accessToken(userId)
                .map(token -> {
                    try {
                        return alerts.sourceStatus(userId, token);
                    } catch (RestClientException e) {
                        return alerts.sourceStatusWithoutGmail(userId);
                    }
                })
                .orElseGet(() -> alerts.sourceStatusWithoutGmail(userId));
    }

    /** Jobs from the user's LinkedIn, Naukri and Indeed alert emails (refreshed by the Gmail sync). */
    @GetMapping("/alerts")
    public List<JobResult> alerts(@AuthenticationPrincipal Jwt jwt) {
        Long userId = CurrentUser.id(jwt);
        return feed.feed(userId, alerts.recent(userId));
    }
}
