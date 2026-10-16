package com.vinod.jobtracker.jobs;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Job search through the Adzuna API (free key at developer.adzuna.com), which lists Indian jobs.
 * LinkedIn and Naukri have no public job search API, and scraping them is against their terms.
 */
@Service
public class JobSearchService {

    private static final Logger log = LoggerFactory.getLogger(JobSearchService.class);
    private static final ObjectMapper JSON = new ObjectMapper();

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

    /** source: ADZUNA, LINKEDIN or NAUKRI (the last two come from the user's alert emails). */
    public record JobResult(String id, String source, String title, String company, String location, String description,
                            String url, String postedAt, Double salaryMin, Double salaryMax,
                            int matchScore, List<String> matchedSkills, boolean fresherFriendly, boolean saved) {
    }

    /** Jobs from the user's LinkedIn / Naukri alert emails, best skill match first, then newest. */
    public List<JobResult> alertJobs(Long userId, List<AlertJob> alerts) {
        List<String> skills = UserService.skillList(userService.get(userId));
        Set<String> savedUrls = savedUrls(userId);
        List<JobResult> results = new ArrayList<>();
        for (AlertJob job : alerts) {
            String text = job.getTitle() + " " + Objects.requireNonNullElse(job.getCompany(), "");
            SkillMatcher.Match match = SkillMatcher.match(skills, text);
            results.add(new JobResult("alert-" + job.getId(), job.getSource().name(), job.getTitle(),
                    Objects.requireNonNullElse(job.getCompany(), ""), Objects.requireNonNullElse(job.getLocation(), ""),
                    "", job.getUrl(), job.getReceivedAt().toString(), null, null, match.score(), match.matched(),
                    SkillMatcher.fresherFriendly(job.getTitle()), savedUrls.contains(job.getUrl())));
        }
        results.sort(Comparator.comparingInt(JobResult::matchScore).reversed()
                .thenComparing(JobResult::postedAt, Comparator.reverseOrder()));
        return results;
    }

    private Set<String> savedUrls(Long userId) {
        return applications.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(JobApplication::getJobUrl).filter(Objects::nonNull).collect(Collectors.toSet());
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
                // Without this Adzuna may answer with an HTML page instead of JSON
                .queryParam("content-type", "application/json")
                .queryParam("what", searchWords(what));
        if (where != null && !where.isBlank()) {
            uri.queryParam("where", where.trim());
        }
        URI target = uri.encode().buildAndExpand(adzuna.country(), safePage).toUri();

        String raw;
        try {
            // Read as text and parse ourselves, so a wrong Content-Type header can't break it
            raw = rest.get().uri(target)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.USER_AGENT, "job-tracker/1.0 (+https://github.com/Vinod5134/job-tracker)")
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException e) {
            // Log Adzuna's own reason (the URL with the keys is not part of this message)
            log.warn("Adzuna search failed: HTTP {} {}", e.getStatusCode().value(),
                    e.getResponseBodyAsString().replaceAll("\\s+", " "));
            int code = e.getStatusCode().value();
            if (code == 401 || code == 403) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Adzuna rejected the API keys. Check ADZUNA_APP_ID and ADZUNA_APP_KEY in Render.");
            }
            if (code == 400) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Adzuna couldn't run that search. Try a simpler title or a different city.");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Job search is unavailable right now (Adzuna returned HTTP " + code + "). Try again.");
        } catch (ResourceAccessException e) {
            log.warn("Adzuna search failed: no response: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Adzuna didn't respond in time. Try again.");
        } catch (RestClientException e) {
            log.warn("Adzuna search failed: unreadable reply", e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Couldn't read Adzuna's reply (" + e.getClass().getSimpleName() + "). Try again.");
        }
        if (raw == null || raw.isBlank()) {
            return new JobSearchResponse(0, safePage, List.of());
        }
        JsonNode body;
        try {
            body = JSON.readTree(raw);
        } catch (JsonProcessingException e) {
            // Log the start of what came back (an HTML page title usually says why)
            String start = raw.replaceAll("\\s+", " ");
            log.warn("Adzuna sent a non-JSON reply: {}", start.substring(0, Math.min(300, start.length())));
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Adzuna sent an unexpected reply. Check the keys in Render, or try again later.");
        }

        List<String> skills = UserService.skillList(userService.get(userId));
        Set<String> savedUrls = savedUrls(userId);

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
                    "ADZUNA",
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

    /**
     * Adzuna needs every word to appear in the ad, and words like "fresher" rarely do,
     * so they're dropped here (the "Fresher-friendly only" filter covers that instead).
     */
    static String searchWords(String what) {
        String cleaned = what == null ? "" : what
                .replaceAll("(?i)\\b(freshers?|entry[- ]level|graduates?|0\\s*-\\s*[12]\\s*(yrs?|years?)|jobs?|openings?)\\b", " ")
                .replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? "java developer" : cleaned;
    }

    private static String stripTags(String s) {
        return s.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").trim();
    }
}
