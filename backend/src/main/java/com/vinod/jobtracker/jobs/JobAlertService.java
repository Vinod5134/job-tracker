package com.vinod.jobtracker.jobs;

import com.vinod.jobtracker.gmail.GmailClient;
import com.vinod.jobtracker.gmail.GmailClient.GmailMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Collects jobs from the user's LinkedIn / Naukri job-alert emails during the Gmail sync.
 * LinkedIn and Naukri have no public job API, and scraping their sites breaks their terms,
 * but the alerts they email the user are the user's own data.
 */
@Service
public class JobAlertService {

    private static final Logger log = LoggerFactory.getLogger(JobAlertService.class);

    /** Alert senders. Other Naukri/LinkedIn mail is skipped later if it holds no job links. */
    static final String ALERT_QUERY = "-in:sent {from:jobalerts-noreply@linkedin.com from:jobs-listings@linkedin.com "
            + "from:jobs-noreply@linkedin.com from:naukri.com from:indeed.com}";
    private static final int MAX_EMAILS_PER_SYNC = 20;
    private static final int KEEP_DAYS = 14;

    private final GmailClient gmail;
    private final JobAlertParser parser;
    private final AlertJobRepository alertJobs;

    public JobAlertService(GmailClient gmail, JobAlertParser parser, AlertJobRepository alertJobs) {
        this.gmail = gmail;
        this.parser = parser;
        this.alertJobs = alertJobs;
    }

    /** Reads alert emails received since the given time and stores new jobs. Returns how many were added. */
    public int syncAlerts(Long userId, String accessToken, Instant since) {
        List<String> ids = gmail.listMessageIds(accessToken,
                "after:" + since.getEpochSecond() + " " + ALERT_QUERY, MAX_EMAILS_PER_SYNC);
        int added = 0;
        for (String id : ids) {
            GmailMessage meta = gmail.getMessage(accessToken, id);
            List<JobAlertParser.ParsedJob> jobs = parser.parse(gmail.getHtmlBody(accessToken, id));
            for (JobAlertParser.ParsedJob job : jobs) {
                String hash = sha256(job.url());
                if (alertJobs.existsByUserIdAndUrlHash(userId, hash)) {
                    continue;
                }
                AlertJob entity = new AlertJob();
                entity.setUserId(userId);
                entity.setSource(job.source());
                entity.setTitle(job.title());
                entity.setCompany(job.company());
                entity.setLocation(job.location());
                entity.setUrl(job.url());
                entity.setUrlHash(hash);
                entity.setGmailMessageId(id);
                entity.setReceivedAt(meta.receivedAt());
                try {
                    alertJobs.save(entity);
                    added++;
                } catch (DataIntegrityViolationException duplicate) {
                    // Same job saved by a parallel sync
                }
            }
        }
        long removed = alertJobs.deleteByUserIdAndReceivedAtBefore(userId, Instant.now().minus(KEEP_DAYS, ChronoUnit.DAYS));
        log.info("Job alerts for user {}: {} emails, {} new jobs, {} expired", userId, ids.size(), added, removed);
        return added;
    }

    /** Gmail searches that find each site's job-alert emails (not application confirmations). */
    private static final Map<JobAlertParser.Source, String> ALERT_SENDERS = Map.of(
            JobAlertParser.Source.LINKEDIN, "{from:jobalerts-noreply@linkedin.com from:jobs-listings@linkedin.com}",
            JobAlertParser.Source.NAUKRI, "from:naukri.com {subject:alert subject:\"jobs for you\" subject:recommended "
                    + "subject:\"jobs matching\" subject:\"new jobs\" subject:\"job matches\"}",
            JobAlertParser.Source.INDEED, "from:indeed.com {subject:jobs subject:alert subject:\"new job\"}");

    public record AlertSourceStatus(JobAlertParser.Source source, boolean subscribed, Instant lastAlertAt, long jobs) {
    }

    /**
     * For each site: has an alert email arrived in the last 14 days? Checked in Gmail directly,
     * so a site counts as set up even if no jobs could be read from its emails yet.
     */
    public List<AlertSourceStatus> sourceStatus(Long userId, String accessToken) {
        List<AlertSourceStatus> result = new ArrayList<>();
        for (JobAlertParser.Source source : JobAlertParser.Source.values()) {
            List<String> ids = gmail.listMessageIds(accessToken,
                    "newer_than:" + KEEP_DAYS + "d -in:sent " + ALERT_SENDERS.get(source), 1);
            Instant last = ids.isEmpty() ? null : gmail.getMessage(accessToken, ids.get(0)).receivedAt();
            long jobs = alertJobs.countByUserIdAndSource(userId, source);
            result.add(new AlertSourceStatus(source, last != null || jobs > 0, last, jobs));
        }
        return result;
    }

    /** Without Gmail we can still say how many jobs each site gave us so far. */
    public List<AlertSourceStatus> sourceStatusWithoutGmail(Long userId) {
        List<AlertSourceStatus> result = new ArrayList<>();
        for (JobAlertParser.Source source : JobAlertParser.Source.values()) {
            long jobs = alertJobs.countByUserIdAndSource(userId, source);
            result.add(new AlertSourceStatus(source, jobs > 0, null, jobs));
        }
        return result;
    }

    public List<AlertJob> recent(Long userId) {
        return alertJobs.findTop150ByUserIdOrderByReceivedAtDesc(userId);
    }

    public void deleteAll(Long userId) {
        alertJobs.deleteByUserId(userId);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
