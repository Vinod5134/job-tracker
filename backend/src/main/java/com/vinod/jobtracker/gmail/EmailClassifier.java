package com.vinod.jobtracker.gmail;

import com.vinod.jobtracker.application.ApplicationStatus;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Rule-based classifier: reads an email's sender, subject and short preview and decides
 * whether it's about a job application, what the status is, and which company it's from.
 *
 * It's deliberately simple and explainable (keyword rules, no ML). When it isn't sure,
 * it returns highConfidence=false and the email goes to "Needs review" instead of
 * changing the board.
 */
@Component
public class EmailClassifier {

    public record EmailInsight(ApplicationStatus status, String company, String role, boolean highConfidence) {
    }

    /** Subjects of marketing / alert emails that look job-related but aren't about an application. */
    private static final List<Pattern> IGNORE_SUBJECT = compile(
            "job alert", "jobs? for you", "recommended jobs?", "jobs? matching", "matching your profile",
            "apply now", "new jobs?", "\\b(is|are) hiring\\b", "viewed your profile", "newsletter", "webinar",
            "\\botp\\b", "verify your (email|account)", "password", "complete your profile", "similar jobs",
            "jobs you may", "top jobs");

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
            "applied successfully");

    /** Phrases like "if you are shortlisted, we will call you for an interview" don't mean an interview. */
    private static final Pattern CONDITIONAL = Pattern.compile(
            "if (you are |you're |your profile is )?(shortlisted|selected)[^.]*", Pattern.CASE_INSENSITIVE);

    /** Job boards: their sender name is the board, not the employer. */
    private static final Set<String> JOB_BOARDS = Set.of(
            "naukri", "linkedin", "indeed", "foundit", "monster", "monsterindia", "shine", "instahyre", "cutshort",
            "wellfound", "angel", "internshala", "hirist", "iimjobs", "apna", "glassdoor", "timesjobs", "freshersworld");

    /** Hiring tools and free mail: the sender name may be the employer, but the domain isn't. */
    private static final Set<String> NON_EMPLOYER_DOMAINS = Set.of(
            "gmail", "googlemail", "yahoo", "outlook", "hotmail", "live", "rediffmail", "icloud", "google",
            "myworkday", "myworkdayjobs", "workday", "greenhouse", "greenhouse-mail", "lever", "hire", "smartrecruiters",
            "icims", "successfactors", "taleo", "oraclecloud", "keka", "darwinbox", "zohorecruit", "zoho", "freshteam",
            "hackerrank", "hackerearth", "mettl", "codility", "amcat", "email", "mail", "mailer", "sendgrid", "amazonses");

    private static final Set<String> NOT_A_COMPANY = Set.of(
            "the", "our", "your", "you", "us", "we", "team", "naukri", "linkedin", "indeed", "interview", "application",
            "applications", "job", "jobs", "update", "status", "invitation", "hiring", "careers", "career", "hr",
            "re", "fwd", "fw", "regarding", "important", "action required", "reminder", "next steps", "thank you");

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

        for (Pattern p : IGNORE_SUBJECT) {
            if (p.matcher(safeSubject).find()) {
                return null;
            }
        }

        String text = CONDITIONAL.matcher(safeSubject + " . " + safeSnippet).replaceAll(" ");
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

        CompanyGuess company = companyFromSubject(safeSubject);
        if (company == null) {
            company = companyFromSender(from);
        }
        String role = roleFromSubject(safeSubject);

        boolean high = !ambiguous && company != null && company.reliable();
        return new EmailInsight(status, company == null ? null : company.name(), role, high);
    }

    private record CompanyGuess(String name, boolean reliable) {
    }

    private CompanyGuess companyFromSubject(String subject) {
        // "at X" is the most reliable ("... Java Developer at Capgemini"), "to X" the least
        for (String preposition : List.of("at", "with", "from", "by", "to")) {
            Matcher m = Pattern.compile("\\b" + preposition + "\\s+" + NAME.pattern()).matcher(subject);
            while (m.find()) {
                String candidate = clean(m.group(1));
                if (candidate != null) {
                    return new CompanyGuess(candidate, true);
                }
            }
        }
        Matcher prefix = SUBJECT_PREFIX.matcher(subject);
        if (prefix.find()) {
            String candidate = clean(prefix.group(1));
            if (candidate != null) {
                return new CompanyGuess(candidate, true);
            }
        }
        return null;
    }

    private CompanyGuess companyFromSender(String from) {
        if (from == null || from.isBlank()) {
            return null;
        }
        String displayName = from.contains("<") ? from.substring(0, from.indexOf('<')).replace("\"", "").trim() : "";
        String address = from.contains("<") ? from.substring(from.indexOf('<') + 1, from.indexOf('>') > 0 ? from.indexOf('>') : from.length())
                : from.trim();
        String domainLabel = domainLabel(address);
        if (domainLabel != null && JOB_BOARDS.contains(domainLabel)) {
            // e.g. Naukri / LinkedIn notifications: the employer must come from the subject
            return null;
        }

        if (!displayName.isEmpty()) {
            Matcher hiring = HIRING_WORDS.matcher(displayName);
            boolean looksLikeOrg = hiring.find();
            String cleaned = clean(HIRING_WORDS.matcher(displayName).replaceAll(" ").replaceAll("\\s+", " ").trim());
            if (cleaned != null) {
                boolean matchesDomain = domainLabel != null && !NON_EMPLOYER_DOMAINS.contains(domainLabel)
                        && cleaned.toLowerCase(Locale.ROOT).replace(" ", "").contains(domainLabel);
                // A plain person's name ("Priya Sharma") is a weak signal, so it needs review
                return new CompanyGuess(cleaned, looksLikeOrg || matchesDomain);
            }
        }
        if (domainLabel != null && !NON_EMPLOYER_DOMAINS.contains(domainLabel)) {
            String name = domainLabel.length() <= 4 ? domainLabel.toUpperCase(Locale.ROOT)
                    : Character.toUpperCase(domainLabel.charAt(0)) + domainLabel.substring(1);
            return new CompanyGuess(name, true);
        }
        return null;
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
        List<String> words = new ArrayList<>(List.of(c.split("\\s+")));
        if (NOT_A_COMPANY.contains(words.get(0).toLowerCase(Locale.ROOT))) {
            return null;
        }
        return c;
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
