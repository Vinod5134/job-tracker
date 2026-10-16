package com.vinod.jobtracker.gmail;

import com.vinod.jobtracker.application.ApplicationStatus;
import com.vinod.jobtracker.application.CompanyNames;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Rule-based classifier: decides whether an email is a real update about one of the user's
 * job applications, and if so the status and company.
 *
 * Trust comes from the sender first, the subject second:
 * - EMPLOYER: the company's own domain (careers@tcs.com) or a hiring tool (Workday, Greenhouse...)
 *   -> any status, board updates automatically when the company is clear
 * - JOB_BOARD: Naukri, LinkedIn, Indeed... -> only real application confirmations count;
 *   interview/offer claims from a board go to "Needs review" (boards relay recruiter mass mail)
 * - PERSONAL: gmail/outlook senders (recruiters, consultancies) -> interview/offer only, always reviewed
 * - BLOCKED / unknown sites: ignored (review sites, newsletters, "job update" spam)
 *
 * It's deliberately simple and explainable (keyword rules, no ML). When it isn't sure,
 * it returns highConfidence=false so the email waits in "Needs review".
 */
@Component
public class EmailClassifier {

    public record EmailInsight(ApplicationStatus status, String company, String role, boolean highConfidence) {
    }

    enum SenderType { EMPLOYER, JOB_BOARD, PERSONAL, BLOCKED, UNKNOWN }

    /** Marketing / alert subjects that look job-related but aren't about one of his applications. */
    private static final List<Pattern> IGNORE_SUBJECT = compile(
            "job alert", "jobs? for you", "recommended jobs?", "jobs? matching", "matching your profile",
            "apply now", "new jobs?", "\\b(is|are) hiring\\b", "viewed your profile", "newsletter", "webinar",
            "\\botp\\b", "verify your (email|account)", "password", "complete your profile", "similar jobs",
            "jobs you may", "top jobs", "what employees", "employees have to say", "company reviews", "write a review",
            "salary insights", "invites? you to apply", "you may be (a )?(good )?fit", "people (also )?viewed");

    /** Bulk-hiring ads. Ignored unless they come from the employer itself. */
    private static final List<Pattern> MARKETING_SUBJECT = compile(
            "walk[- ]?ins?\\b", "\\bregister\\b", "hiring drive", "job fair", "urgent(ly)? (hiring|requirement|opening)",
            "openings?\\b", "vacanc", "\\bwebinar\\b", "\\bmega\\b", "reminder");

    /** On job boards, only these subjects are real updates about an application he made. */
    private static final List<Pattern> BOARD_CONFIRMATION = compile(
            "application (was |has been )?(sent|submitted|received|viewed)", "(you|you've|you have) (successfully )?applied",
            "applied (to|for)", "received your application", "viewed your application", "application status",
            "status of your application", "shortlisted", "interview", "not (been )?(selected|shortlisted)",
            "unfortunately", "regret", "offer");

    private static final List<Pattern> OFFER = compile(
            "offer letter", "pleased to offer", "extend(ing)? (you )?(an|the|a) offer", "job offer",
            "offer of employment", "congratulations[^.]{0,80}\\boffer", "selected for the (position|role|post|job)");

    private static final List<Pattern> REJECTED = compile(
            "unfortunately", "regret to inform", "not (been )?(selected|shortlisted)", "not (be )?moving forward",
            "will not be proceeding", "decided to (move|proceed|go) (forward )?with other", "other candidates",
            "position has been filled", "unable to (move|proceed)", "no longer under consideration", "not a (good )?fit");

    private static final List<Pattern> INTERVIEW = compile(
            "interview", "shortlisted", "assessment", "online test", "coding (test|challenge|round)",
            "technical round", "hr round", "next round", "aptitude test", "hackerrank", "hackerearth", "codility", "mettl");

    private static final List<Pattern> APPLIED = compile(
            "thank(s| you) for applying", "thank you for your (interest|application)",
            "application (has been |was )?(received|submitted|sent|viewed)", "applied (for|to)", "you applied",
            "your application (for|to|at|with)", "successfully applied", "received your application",
            "applied successfully", "viewed your application");

    /** Phrases like "if you are shortlisted, we will call you for an interview" don't mean an interview. */
    private static final Pattern CONDITIONAL = Pattern.compile(
            "if (you are |you're |your profile is )?(shortlisted|selected)[^.]*", Pattern.CASE_INSENSITIVE);

    /** Job boards: real confirmations come from here, but so does a lot of marketing. */
    private static final Set<String> JOB_BOARDS = Set.of(
            "naukri", "linkedin", "indeed", "foundit", "monster", "monsterindia", "shine", "instahyre", "cutshort",
            "wellfound", "internshala", "hirist", "iimjobs", "apna", "timesjobs", "freshersworld", "unstop");

    /** Hiring tools that send mail on the employer's behalf: treated like the employer. */
    private static final Set<String> HIRING_TOOLS = Set.of(
            "myworkday", "myworkdayjobs", "workday", "greenhouse", "greenhouse-mail", "lever", "hire", "smartrecruiters",
            "icims", "successfactors", "taleo", "oraclecloud", "keka", "darwinbox", "zohorecruit", "freshteam",
            "hackerrank", "hackerearth", "mettl", "codility", "amcat", "superset", "eightfold", "phenom", "jobvite");

    private static final Set<String> FREE_MAIL = Set.of(
            "gmail", "googlemail", "yahoo", "outlook", "hotmail", "live", "rediffmail", "icloud", "proton", "protonmail");

    /** Review sites, aggregators and bulk "job update" senders that aren't about his own applications. */
    private static final Set<String> BLOCKED = Set.of(
            "ambitionbox", "glassdoor", "skilluo", "techslash", "jobhai", "workindia", "quikr", "olx", "timesascent",
            "jooble", "adzuna", "careerjet", "simplyhired", "talent", "jobrapido", "whatjobs", "google", "youtube",
            "coursera", "udemy", "unacademy", "simplilearn", "scaler", "geeksforgeeks", "medium", "substack");

    private static final Set<String> NOT_A_COMPANY = Set.of(
            "the", "our", "your", "you", "us", "we", "team", "naukri", "linkedin", "indeed", "interview", "application",
            "applications", "job", "jobs", "update", "status", "invitation", "hiring", "careers", "career", "hr",
            "re", "fwd", "fw", "regarding", "important", "action required", "reminder", "next steps", "thank you",
            "hi", "hello", "dear", "hey", "congratulations", "urgent", "new", "your application");

    private static final Pattern NAME = Pattern.compile(
            "([A-Z0-9][\\w&.'-]*(?:\\s+(?:[A-Z0-9][\\w&.'-]*|&|of|and)){0,4})");

    private static final Pattern SUBJECT_PREFIX = Pattern.compile(
            "^([A-Z][\\w&.'-]*(?:\\s+[A-Z][\\w&.'-]*){0,3})\\s*[-|:–]\\s");

    private static final Pattern HIRING_WORDS = Pattern.compile(
            "\\b(careers?|hr|human resources|recruit(ment|ing|er|ers)?|talent( acquisition)?|acquisition|team|jobs?|"
                    + "hiring|no-?reply|notifications?|via)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern ROLE = Pattern.compile(
            "\\b(?:for|as)\\s+(?:the\\s+|a\\s+|an\\s+)?(?:position\\s+of\\s+|role\\s+of\\s+|post\\s+of\\s+)?"
                    + "([A-Za-z][A-Za-z0-9 /().+#-]{2,60}?)\\s*(?:\\b(?:at|with|in|role|position|post|opening)\\b|[-|:,]|$)");

    private static final Pattern ROLE_WORDS = Pattern.compile(
            "\\b(engineer|developer|analyst|trainee|intern|associate|consultant|programmer|tester|administrator|"
                    + "architect|designer|executive|specialist|lead|scientist|sde|get)\\b", Pattern.CASE_INSENSITIVE);

    public EmailInsight classify(String from, String subject, String snippet) {
        String safeSubject = subject == null ? "" : subject.trim();
        String safeSnippet = snippet == null ? "" : snippet;
        Sender sender = parseSender(from);

        if (sender.type() == SenderType.BLOCKED || sender.type() == SenderType.UNKNOWN) {
            return null;
        }
        if (matchesAny(IGNORE_SUBJECT, safeSubject)) {
            return null;
        }
        if (sender.type() != SenderType.EMPLOYER && matchesAny(MARKETING_SUBJECT, safeSubject)) {
            return null;
        }
        // Boards send far more marketing than confirmations: the subject itself must be a confirmation
        if (sender.type() == SenderType.JOB_BOARD && !matchesAny(BOARD_CONFIRMATION, safeSubject)) {
            return null;
        }

        // For job boards and personal senders, trust only the subject (previews contain ads and footers)
        String text = sender.type() == SenderType.EMPLOYER ? safeSubject + " . " + safeSnippet : safeSubject;
        text = CONDITIONAL.matcher(text).replaceAll(" ");
        EnumSet<ApplicationStatus> found = EnumSet.noneOf(ApplicationStatus.class);
        if (matchesAny(OFFER, text)) {
            found.add(ApplicationStatus.OFFER);
        }
        if (matchesAny(REJECTED, text)) {
            found.add(ApplicationStatus.REJECTED);
        }
        if (matchesAny(INTERVIEW, text)) {
            found.add(ApplicationStatus.INTERVIEW);
        }
        if (matchesAny(APPLIED, text)) {
            found.add(ApplicationStatus.APPLIED);
        }
        if (found.isEmpty()) {
            return null;
        }

        // Priority: offer > rejected > interview > applied
        ApplicationStatus status = found.contains(ApplicationStatus.OFFER) ? ApplicationStatus.OFFER
                : found.contains(ApplicationStatus.REJECTED) ? ApplicationStatus.REJECTED
                : found.contains(ApplicationStatus.INTERVIEW) ? ApplicationStatus.INTERVIEW
                : ApplicationStatus.APPLIED;
        // "Thanks for applying ... interview process" could be either, so ask the user
        boolean ambiguous = status == ApplicationStatus.INTERVIEW && found.contains(ApplicationStatus.APPLIED);

        String company = companyFromSubject(safeSubject);
        String role = roleFromSubject(safeSubject);

        return switch (sender.type()) {
            case EMPLOYER -> {
                if (company == null) {
                    company = sender.companyName();
                }
                // The company in the subject must be the sender's own company (or come via a hiring tool),
                // otherwise someone else is talking about that company
                boolean consistent = sender.viaHiringTool() || company == null
                        || sender.domainLabel() == null || sameAsDomain(company, sender.domainLabel())
                        || (sender.companyName() != null && CompanyNames.sameCompany(company, sender.companyName()));
                if (!consistent) {
                    yield null;
                }
                yield new EmailInsight(status, company, role, !ambiguous && company != null);
            }
            case JOB_BOARD -> {
                if (company == null) {
                    yield null;
                }
                // A board relaying "interview" or "offer" is often recruiter mass mail: let the user confirm
                boolean confirmed = status == ApplicationStatus.APPLIED || status == ApplicationStatus.REJECTED;
                yield new EmailInsight(status, company, role, confirmed && !ambiguous);
            }
            case PERSONAL -> status == ApplicationStatus.APPLIED
                    ? null
                    : new EmailInsight(status, company, role, false);
            default -> null;
        };
    }

    /**
     * True when the current rules would skip this email outright, judged on sender and subject only.
     * Used to clear out "Needs review" items saved by older, looser rules.
     */
    public boolean isNoise(String from, String subject) {
        String safeSubject = subject == null ? "" : subject;
        Sender sender = parseSender(from);
        return sender.type() == SenderType.BLOCKED || sender.type() == SenderType.UNKNOWN
                || matchesAny(IGNORE_SUBJECT, safeSubject)
                || (sender.type() != SenderType.EMPLOYER && matchesAny(MARKETING_SUBJECT, safeSubject))
                || (sender.type() == SenderType.JOB_BOARD && !matchesAny(BOARD_CONFIRMATION, safeSubject));
    }

    private record Sender(SenderType type, String domainLabel, String companyName, boolean viaHiringTool) {
    }

    private Sender parseSender(String from) {
        if (from == null || from.isBlank()) {
            return new Sender(SenderType.UNKNOWN, null, null, false);
        }
        String displayName = from.contains("<") ? from.substring(0, from.indexOf('<')).replace("\"", "").trim() : "";
        int close = from.indexOf('>');
        String address = from.contains("<")
                ? from.substring(from.indexOf('<') + 1, close > 0 ? close : from.length())
                : from.trim();
        String label = domainLabel(address);
        if (label == null) {
            return new Sender(SenderType.UNKNOWN, null, null, false);
        }
        if (BLOCKED.contains(label)) {
            return new Sender(SenderType.BLOCKED, label, null, false);
        }
        if (JOB_BOARDS.contains(label)) {
            return new Sender(SenderType.JOB_BOARD, label, null, false);
        }
        if (FREE_MAIL.contains(label)) {
            return new Sender(SenderType.PERSONAL, label, null, false);
        }

        String cleanedName = clean(HIRING_WORDS.matcher(displayName).replaceAll(" ").replaceAll("\\s+", " ").trim());
        if (HIRING_TOOLS.contains(label)) {
            // e.g. "Capgemini Recruiting <no-reply@myworkday.com>": the display name is the employer
            return new Sender(SenderType.EMPLOYER, null, cleanedName, true);
        }
        // A company domain is only trusted when the sender looks like that company's hiring team
        // (display name mentions the company or careers/HR) - this keeps out generic newsletters
        boolean hiringSender = HIRING_WORDS.matcher(displayName).find() || HIRING_WORDS.matcher(address).find()
                || displayName.isEmpty()
                || (cleanedName != null && sameAsDomain(cleanedName, label));
        if (!hiringSender) {
            return new Sender(SenderType.UNKNOWN, label, null, false);
        }
        String domainName = label.length() <= 4 ? label.toUpperCase(Locale.ROOT)
                : Character.toUpperCase(label.charAt(0)) + label.substring(1);
        String company = cleanedName != null && sameAsDomain(cleanedName, label) ? cleanedName : domainName;
        return new Sender(SenderType.EMPLOYER, label, company, false);
    }

    private static boolean sameAsDomain(String company, String domainLabel) {
        String compact = CompanyNames.normalize(company).replace(" ", "");
        String label = domainLabel.toLowerCase(Locale.ROOT).replace("-", "");
        return !compact.isEmpty() && (compact.contains(label) || label.contains(compact));
    }

    /** careers@mail.tcs.com -> "tcs", hr@infosys.co.in -> "infosys". */
    static String domainLabel(String address) {
        int at = address.lastIndexOf('@');
        if (at < 0) {
            return null;
        }
        String[] parts = address.substring(at + 1).toLowerCase(Locale.ROOT).trim().split("\\.");
        if (parts.length < 2) {
            return null;
        }
        String label = parts[parts.length - 2];
        if (Set.of("co", "com", "net", "org", "ac", "gov").contains(label) && parts.length >= 3) {
            label = parts[parts.length - 3];
        }
        return label.isBlank() ? null : label;
    }

    private String companyFromSubject(String subject) {
        // "at X" is the most reliable ("... Java Developer at Capgemini"), "to X" the least
        for (String preposition : List.of("at", "with", "from", "by", "to")) {
            Matcher m = Pattern.compile("\\b" + preposition + "\\s+" + NAME.pattern()).matcher(subject);
            while (m.find()) {
                String candidate = clean(m.group(1));
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        Matcher prefix = SUBJECT_PREFIX.matcher(subject);
        if (prefix.find()) {
            return clean(prefix.group(1));
        }
        return null;
    }

    private String roleFromSubject(String subject) {
        Matcher m = ROLE.matcher(subject);
        while (m.find()) {
            String candidate = m.group(1).trim();
            if (ROLE_WORDS.matcher(candidate).find()) {
                return candidate;
            }
        }
        return null;
    }

    /** Trims punctuation and rejects generic words that aren't company names. */
    private static String clean(String candidate) {
        if (candidate == null) {
            return null;
        }
        String c = candidate.replaceAll("^[\\s\"'.,:;|-]+|[\\s\"'.,:;|!-]+$", "");
        if (c.length() < 2 || c.length() > 80 || NOT_A_COMPANY.contains(c.toLowerCase(Locale.ROOT))) {
            return null;
        }
        String firstWord = c.split("\\s+")[0].toLowerCase(Locale.ROOT);
        return NOT_A_COMPANY.contains(firstWord) ? null : c;
    }

    private static boolean matchesAny(List<Pattern> patterns, String text) {
        for (Pattern p : patterns) {
            if (p.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static List<Pattern> compile(String... regexes) {
        List<Pattern> list = new ArrayList<>();
        for (String r : regexes) {
            list.add(Pattern.compile(r, Pattern.CASE_INSENSITIVE));
        }
        return List.copyOf(list);
    }
}
