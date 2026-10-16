package com.vinod.jobtracker.gmail;

import com.vinod.jobtracker.application.ApplicationDtos.ApplicationResponse;
import com.vinod.jobtracker.application.ApplicationDtos.EmailUpdate;
import com.vinod.jobtracker.application.ApplicationService;
import com.vinod.jobtracker.application.ApplicationStatus;
import com.vinod.jobtracker.common.BadRequestException;
import com.vinod.jobtracker.common.NotFoundException;
import com.vinod.jobtracker.config.AppProperties;
import com.vinod.jobtracker.config.TokenCipher;
import com.vinod.jobtracker.gmail.EmailClassifier.EmailInsight;
import com.vinod.jobtracker.gmail.GmailClient.GmailAuthException;
import com.vinod.jobtracker.gmail.GmailClient.GmailMessage;
import com.vinod.jobtracker.jobs.JobAlertService;
import java.util.Map;
import com.vinod.jobtracker.realtime.LiveUpdateService;
import com.vinod.jobtracker.user.AppUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

/**
 * Reads new job-related emails from Gmail and turns them into board updates:
 * 1. Search Gmail for application/interview/offer emails since the last sync
 * 2. Classify each one (status + company)
 * 3. High confidence -> update the board and push it live; low confidence -> "Needs review"
 */
@Service
public class GmailSyncService {

    private static final Logger log = LoggerFactory.getLogger(GmailSyncService.class);

    /** Gmail search: job-ish subjects or job-board senders, excluding mail he sent. */
    private static final String SEARCH = "-in:sent -in:chats {subject:application subject:applying subject:applied "
            + "subject:interview subject:assessment subject:shortlisted subject:offer subject:candidature "
            + "subject:unfortunately subject:\"next steps\" from:naukri.com from:linkedin.com from:foundit.in "
            + "from:indeed.com from:instahyre.com}";

    private final GmailConnectionRepository connections;
    private final EmailRecordRepository records;
    private final GmailClient gmail;
    private final EmailClassifier classifier;
    private final ApplicationService applicationService;
    private final TokenCipher cipher;
    private final LiveUpdateService live;
    private final AppProperties props;
    private final JobAlertService jobAlerts;

    /** Prevents the scheduler and a "Sync now" click from syncing the same user at once. */
    private final Set<Long> syncing = ConcurrentHashMap.newKeySet();

    public GmailSyncService(GmailConnectionRepository connections, EmailRecordRepository records, GmailClient gmail,
                            EmailClassifier classifier, ApplicationService applicationService, TokenCipher cipher,
                            LiveUpdateService live, AppProperties props, JobAlertService jobAlerts) {
        this.jobAlerts = jobAlerts;
        this.connections = connections;
        this.records = records;
        this.gmail = gmail;
        this.classifier = classifier;
        this.applicationService = applicationService;
        this.cipher = cipher;
        this.live = live;
        this.props = props;
    }

    public record SyncResult(int scanned, int detected, int autoUpdated, int needsReview, boolean skipped) {
    }

    public record EmailRecordResponse(Long id, String from, String subject, Instant receivedAt,
                                      ApplicationStatus detectedStatus, String detectedCompany, String detectedRole,
                                      EmailRecord.State state, Long applicationId) {

        static EmailRecordResponse from(EmailRecord r) {
            return new EmailRecordResponse(r.getId(), r.getFrom(), r.getSubject(), r.getReceivedAt(),
                    r.getDetectedStatus(), r.getDetectedCompany(), r.getDetectedRole(), r.getState(),
                    r.getApplicationId());
        }
    }

    @Transactional
    public void connect(AppUser user, String refreshToken) {
        GmailConnection connection = connections.findByUserId(user.getId()).orElseGet(GmailConnection::new);
        connection.setUserId(user.getId());
        connection.setGoogleEmail(user.getEmail());
        connection.setRefreshTokenEncrypted(cipher.encrypt(refreshToken));
        connection.setActive(true);
        connection.setConnectedAt(Instant.now());
        connection.setLastError(null);
        connections.save(connection);
        live.send(user.getId(), LiveUpdateService.GMAIL_STATUS, status(user.getId()));
    }

    @Transactional
    public void disconnect(Long userId) {
        connections.findByUserId(userId).ifPresent(connection -> {
            gmail.revoke(cipher.decrypt(connection.getRefreshTokenEncrypted()));
            connections.delete(connection);
        });
        records.deleteByUserId(userId);
        jobAlerts.deleteAll(userId);
        live.send(userId, LiveUpdateService.GMAIL_STATUS, GmailStatus.notConnected());
    }

    @Transactional(readOnly = true)
    public GmailStatus status(Long userId) {
        return connections.findByUserId(userId)
                .map(c -> new GmailStatus(c.isActive(), c.getGoogleEmail(), c.getLastSyncedAt(), c.getLastError(),
                        records.countByUserIdAndState(userId, EmailRecord.State.NEEDS_REVIEW)))
                .orElse(GmailStatus.notConnected());
    }

    public SyncResult sync(Long userId) {
        GmailConnection connection = connections.findByUserId(userId)
                .filter(GmailConnection::isActive)
                .orElseThrow(() -> new BadRequestException("Connect Gmail first"));
        if (!syncing.add(userId)) {
            return new SyncResult(0, 0, 0, 0, true);
        }
        try {
            return doSync(connection);
        } finally {
            syncing.remove(userId);
        }
    }

    private SyncResult doSync(GmailConnection connection) {
        Long userId = connection.getUserId();
        Instant startedAt = Instant.now();
        // Overlap by a few minutes so emails arriving during the last sync aren't missed (duplicates are skipped)
        Instant since = connection.getLastSyncedAt() == null
                ? startedAt.minus(props.gmail().firstSyncDays(), ChronoUnit.DAYS)
                : connection.getLastSyncedAt().minus(5, ChronoUnit.MINUTES);

        int scanned = 0;
        int detected = 0;
        int updated = 0;
        int review = 0;
        dismissNoiseInReview(userId);
        try {
            String accessToken = gmail.refreshAccessToken(cipher.decrypt(connection.getRefreshTokenEncrypted()));
            String query = "after:" + since.getEpochSecond() + " " + SEARCH;
            List<String> ids = gmail.listMessageIds(accessToken, query, props.gmail().maxMessagesPerSync());

            for (String id : ids) {
                if (records.existsByUserIdAndGmailMessageId(userId, id)) {
                    continue;
                }
                scanned++;
                GmailMessage message = gmail.getMessage(accessToken, id);
                EmailInsight insight = classifier.classify(message.from(), message.subject(), message.snippet());
                if (insight == null) {
                    continue;
                }
                detected++;

                EmailRecord record = new EmailRecord();
                record.setUserId(userId);
                record.setGmailMessageId(id);
                record.setFrom(message.from());
                record.setSubject(message.subject());
                record.setReceivedAt(message.receivedAt());
                record.setDetectedStatus(insight.status());
                record.setDetectedCompany(insight.company());
                record.setDetectedRole(insight.role());

                if (insight.highConfidence()) {
                    Optional<EmailUpdate> changed = applicationService.applyEmailUpdate(
                            userId, insight.company(), insight.role(), insight.status(), false);
                    if (changed.isPresent()) {
                        record.setState(EmailRecord.State.AUTO_APPLIED);
                        remember(record, changed.get());
                        updated++;
                        notifyDetected(userId, changed.get().application());
                    } else {
                        record.setState(EmailRecord.State.NO_CHANGE);
                    }
                } else {
                    record.setState(EmailRecord.State.NEEDS_REVIEW);
                    review++;
                }
                try {
                    records.save(record);
                } catch (DataIntegrityViolationException duplicate) {
                    // Already stored by a parallel sync
                }
            }

            syncJobAlerts(connection, accessToken, startedAt);
            connection.setLastSyncedAt(startedAt);
            connection.setLastError(null);
        } catch (GmailAuthException e) {
            connection.setActive(false);
            connection.setLastError("Gmail access was removed. Connect Gmail again.");
            connections.save(connection);
            live.send(userId, LiveUpdateService.GMAIL_STATUS, status(userId));
            throw new BadRequestException("Gmail access was removed. Connect Gmail again.");
        } catch (RestClientException e) {
            log.warn("Gmail sync failed for user {}: {}", userId, e.getMessage());
            connection.setLastError("Couldn't reach Gmail. It will retry automatically.");
            connections.save(connection);
            throw new BadRequestException("Couldn't reach Gmail. Try again in a minute.");
        }
        connections.save(connection);

        if (review > 0) {
            live.send(userId, LiveUpdateService.EMAILS_CHANGED, status(userId));
            live.notify(userId, "Emails need review", review + " job email(s) need a quick check", "info");
        }
        live.send(userId, LiveUpdateService.GMAIL_STATUS, status(userId));
        return new SyncResult(scanned, detected, updated, review, false);
    }

    /** Job-alert emails feed "Find jobs"; a problem there must not break status syncing. */
    private void syncJobAlerts(GmailConnection connection, String accessToken, Instant startedAt) {
        Instant since = connection.getAlertsSyncedAt() == null
                ? startedAt.minus(7, ChronoUnit.DAYS)
                : connection.getAlertsSyncedAt().minus(5, ChronoUnit.MINUTES);
        try {
            int added = jobAlerts.syncAlerts(connection.getUserId(), accessToken, since);
            connection.setAlertsSyncedAt(startedAt);
            if (added > 0) {
                live.send(connection.getUserId(), LiveUpdateService.ALERT_JOBS, Map.of("added", added));
            }
        } catch (RuntimeException e) {
            log.warn("Job alert sync failed for user {}: {}", connection.getUserId(), e.toString());
        }
    }

    /** Emails queued for review under older, looser rules that the current rules would ignore. */
    private void dismissNoiseInReview(Long userId) {
        for (EmailRecord record : records.findTop50ByUserIdAndStateInOrderByReceivedAtDesc(
                userId, List.of(EmailRecord.State.NEEDS_REVIEW))) {
            if (classifier.isNoise(record.getFrom(), record.getSubject())) {
                record.setState(EmailRecord.State.DISMISSED);
                records.save(record);
            }
        }
    }

    private void notifyDetected(Long userId, ApplicationResponse app) {
        String title = switch (app.status()) {
            case INTERVIEW -> "Interview detected";
            case OFFER -> "Offer detected";
            case REJECTED -> "Application update";
            default -> "Application detected";
        };
        String level = app.status() == ApplicationStatus.OFFER ? "success"
                : app.status() == ApplicationStatus.REJECTED ? "warning" : "info";
        live.notify(userId, title, app.company() + " is now " + app.status().label(), level);
    }

    /** Background sync for every connected user. */
    @Scheduled(fixedDelayString = "${app.gmail.sync-interval-ms}", initialDelay = 60_000)
    public void syncAll() {
        for (GmailConnection connection : connections.findByActiveTrue()) {
            try {
                sync(connection.getUserId());
            } catch (RuntimeException e) {
                log.info("Scheduled Gmail sync skipped for user {}: {}", connection.getUserId(), e.getMessage());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<EmailRecordResponse> list(Long userId, Collection<EmailRecord.State> states) {
        return records.findTop50ByUserIdAndStateInOrderByReceivedAtDesc(userId, states).stream()
                .map(EmailRecordResponse::from).toList();
    }

    /** The user confirmed (and maybe corrected) a "Needs review" email. */
    @Transactional
    public EmailRecordResponse accept(Long userId, Long recordId, String company, ApplicationStatus status, String role) {
        EmailRecord record = records.findByIdAndUserId(recordId, userId)
                .orElseThrow(() -> new NotFoundException("Email not found"));
        if (company == null || company.isBlank()) {
            throw new BadRequestException("Enter the company name");
        }
        if (status == null) {
            throw new BadRequestException("Choose a status");
        }
        applicationService.applyEmailUpdate(userId, company.trim(), blankToNull(role), status, true)
                .ifPresent(update -> remember(record, update));
        record.setDetectedCompany(company.trim());
        record.setDetectedStatus(status);
        record.setDetectedRole(blankToNull(role));
        record.setState(EmailRecord.State.ACCEPTED);
        EmailRecord saved = records.save(record);
        live.send(userId, LiveUpdateService.EMAILS_CHANGED, status(userId));
        return EmailRecordResponse.from(saved);
    }

    /** Reverts a board change made from this email (wrong company, not a real update, ...). */
    @Transactional
    public EmailRecordResponse undo(Long userId, Long recordId) {
        EmailRecord record = records.findByIdAndUserId(recordId, userId)
                .orElseThrow(() -> new NotFoundException("Email not found"));
        boolean changedBoard = record.getState() == EmailRecord.State.AUTO_APPLIED
                || record.getState() == EmailRecord.State.ACCEPTED;
        if (!changedBoard || record.getApplicationId() == null) {
            throw new BadRequestException("This email didn't change your board");
        }
        applicationService.revertEmailUpdate(userId, record.getApplicationId(), record.getCreatedCard(),
                record.getPreviousStatus(), record.getDetectedStatus());
        record.setState(EmailRecord.State.DISMISSED);
        EmailRecord saved = records.save(record);
        live.send(userId, LiveUpdateService.EMAILS_CHANGED, status(userId));
        return EmailRecordResponse.from(saved);
    }

    private static void remember(EmailRecord record, EmailUpdate update) {
        record.setApplicationId(update.application().id());
        record.setPreviousStatus(update.previousStatus());
        record.setCreatedCard(update.created());
    }

    @Transactional
    public void dismiss(Long userId, Long recordId) {
        EmailRecord record = records.findByIdAndUserId(recordId, userId)
                .orElseThrow(() -> new NotFoundException("Email not found"));
        record.setState(EmailRecord.State.DISMISSED);
        records.save(record);
        live.send(userId, LiveUpdateService.EMAILS_CHANGED, status(userId));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
