package com.vinod.jobtracker.jobs;

import com.vinod.jobtracker.config.CurrentUser;
import com.vinod.jobtracker.jobs.JobSearchService.JobResult;
import com.vinod.jobtracker.jobs.JobSearchService.JobSearchResponse;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobSearchService service;
    private final JobAlertService alerts;

    public JobController(JobSearchService service, JobAlertService alerts) {
        this.service = service;
        this.alerts = alerts;
    }

    @GetMapping("/search")
    public JobSearchResponse search(@AuthenticationPrincipal Jwt jwt,
                                    @RequestParam(defaultValue = "java developer fresher") String what,
                                    @RequestParam(required = false) String where,
                                    @RequestParam(defaultValue = "1") int page) {
        return service.search(CurrentUser.id(jwt), what, where, page);
    }

    /** Jobs from the user's LinkedIn / Naukri job-alert emails (refreshed by the Gmail sync). */
    @GetMapping("/alerts")
    public List<JobResult> alerts(@AuthenticationPrincipal Jwt jwt) {
        Long userId = CurrentUser.id(jwt);
        return service.alertJobs(userId, alerts.recent(userId));
    }
}
