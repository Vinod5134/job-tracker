package com.vinod.jobtracker.jobs;

import com.fasterxml.jackson.databind.JsonNode;
import com.vinod.jobtracker.application.JobApplication;
import com.vinod.jobtracker.application.JobApplicationRepository;
import com.vinod.jobtracker.config.AppProperties;
import com.vinod.jobtracker.user.UserService;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Job search through the Adzuna API (free key at developer.adzuna.com), which lists Indian jobs.
 * LinkedIn and Naukri have no public job search API, and scraping them is against their terms.
 */
@Service
public class JobSearchService {

    private static final Logger log = LoggerFactory.getLogger(JobSearchService.class);

    private final RestClient rest;
    private final AppProperties props;
    private final UserService userService;
    private final JobApplicationRepository applications;

    public JobSearchService(RestClient rest, AppProperties props, UserService userService,
                            JobApplicationRepository applications) {
        this.rest = rest;
        this.props = props;
        this.userService = userService;
        this.applications = applications;
    }

    public record JobResult(String id, String title, String company, String location, String description,
                            String url, String postedAt, Double salaryMin, Double salaryMax,
                            int matchScore, List<String> matchedSkills, boolean fresherFriendly, boolean saved) {
    }

    public record JobSearchResponse(long total, int page, List<JobResult> results) {
    }

    public JobSearchResponse search(Long userId, String what, String where, int page) {
        AppProperties.Adzuna adzuna = props.adzuna();
        if (!adzuna.configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Job search isn't set up yet. Add ADZUNA_APP_ID and ADZUNA_APP_KEY to the backend.");
        }
        int safePage = Math.max(1, Math.min(page, 20));
        UriComponentsBuilder uri = UriComponentsBuilder
                .fromUriString("https://api.adzuna.com/v1/api/jobs/{country}/search/{page}")
                .queryParam("app_id", adzuna.appId())
                .queryParam("app_key", adzuna.appKey())
                .queryParam("results_per_page", 20)
                .queryParam("max_days_old", 30)
                .queryParam("sort_by", "date")
                .queryParam("what", what == null || what.isBlank() ? "java developer" : what.trim());
        if (where != null && !where.isBlank()) {
            uri.queryParam("where", where.trim());
        }
        URI target = uri.encode().buildAndExpand(adzuna.country(), safePage).toUri();

        JsonNode body;
        try {
            body = rest.get().uri(target).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            log.warn("Adzuna search failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Job search is unavailable right now. Try again.");
        }
        if (body == null) {
            return new JobSearchResponse(0, safePage, List.of());
        }

        List<String> skills = UserService.skillList(userService.get(userId));
        Set<String> savedUrls = applications.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(JobApplication::getJobUrl).filter(Objects::nonNull).collect(Collectors.toSet());

        List<JobResult> results = new ArrayList<>();
        for (JsonNode job : body.path("results")) {
            String title = stripTags(job.path("title").asText(""));
            String description = stripTags(job.path("description").asText(""));
            String url = job.path("redirect_url").asText("");
            if (!url.startsWith("http")) {
                continue;
            }
            SkillMatcher.Match match = SkillMatcher.match(skills, title + " " + description);
            results.add(new JobResult(
                    job.path("id").asText(),
                    title,
                    job.path("company").path("display_name").asText("Unknown company"),
                    job.path("location").path("display_name").asText(""),
                    description,
                    url,
                    job.path("created").asText(null),
                    job.hasNonNull("salary_min") ? job.get("salary_min").asDouble() : null,
                    job.hasNonNull("salary_max") ? job.get("salary_max").asDouble() : null,
                    match.score(),
                    match.matched(),
                    SkillMatcher.fresherFriendly(title + " " + description),
                    savedUrls.contains(url)));
        }
        results.sort(Comparator.comparingInt(JobResult::matchScore).reversed());
        return new JobSearchResponse(body.path("count").asLong(results.size()), safePage, results);
    }

    private static String stripTags(String s) {
        return s.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
    }
}
