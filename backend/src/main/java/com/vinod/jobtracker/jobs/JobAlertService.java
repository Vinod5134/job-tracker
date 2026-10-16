package com.vinod.jobtracker.jobs;

import com.vinod.jobtracker.gmail.GmailClient;
import com.vinod.jobtracker.gmail.GmailClient.GmailMessage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
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
