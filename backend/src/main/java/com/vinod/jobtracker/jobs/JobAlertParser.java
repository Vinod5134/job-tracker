package com.vinod.jobtracker.jobs;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Pulls job postings out of LinkedIn and Naukri job-alert emails.
 *
 * Email layouts change over time, so this doesn't depend on CSS classes. It finds every link
 * to a job page (linkedin.com/jobs/view/123, naukri.com/job-listings-...), takes the link
 * text as the title, and reads company/location from the text of the same "card".
 */
@Component
public class JobAlertParser {

    public enum Source { LINKEDIN, NAUKRI, INDEED }

    public record ParsedJob(Source source, String title, String company, String location, String url) {
    }

    private record JobLink(Source source, String key, String url) {
    }

    private static final Pattern LINKEDIN_JOB = Pattern.compile("linkedin\\.com/(?:comm/)?jobs/view/(\\d{6,})");
    private static final Pattern NAUKRI_JOB = Pattern.compile("naukri\\.com/(job-listings-[a-z0-9-]+?)(?:[?#&\"']|$)");
    /** Indeed job links carry the job key as jk=16 hex characters (viewjob, rc/clk, pagead/clk...). */
    private static final Pattern INDEED_JOB = Pattern.compile("indeed\\.com/[^\\s\"']*?[?&]jk=([a-f0-9]{16})");

    /** Link texts that are buttons, not job titles. */
    private static final Set<String> NOT_TITLES = Set.of(
            "apply", "apply now", "easy apply", "view job", "view jobs", "view", "see job", "see all jobs", "view all jobs",
            "see more jobs", "search jobs", "unsubscribe", "manage alerts", "job alert settings", "save", "more",
            "view similar jobs", "show more", "login", "sign in", "help", "naukri", "linkedin", "click here");

    /** Card text that isn't company or location. */
    private static final Pattern NOISE = Pattern.compile(
            "(?i)^(actively recruiting|easy apply|easily apply|urgently hiring|responsive employer|hiring multiple candidates|"
                    + "promoted|be an early applicant|\\d+ (connections?|alumni|applicants?).*|"
                    + "new|apply|apply now|view job|reviews?|\\d+(\\.\\d)? ?\u2605?|\\(\\d+ reviews?\\)|"
                    + ".*days? ago|today|just now|posted.*|hot job|featured|\\d+ (day|hour|week)s? ago)$");
    private static final Pattern EXPERIENCE = Pattern.compile("(?i)\\b\\d+\\s*(-|to)\\s*\\d+\\s*(yrs?|years?)\\b|\\bfresher\\b");
    private static final Pattern SALARY = Pattern.compile(
            "(?i)(\u20b9|\\blpa\\b|lacs?\\b|lakhs?\\b|not disclosed|per annum|a year|a month|per month)");

    public List<ParsedJob> parse(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        Document doc = Jsoup.parse(html);
        Map<String, ParsedJob> jobs = new LinkedHashMap<>();

        for (Element anchor : doc.select("a[href]")) {
            Optional<JobLink> link = jobLink(anchor.attr("href"));
            if (link.isEmpty()) {
                continue;
            }
            JobLink job = link.get();
            String text = tidy(anchor.text());
            ParsedJob existing = jobs.get(job.key());
            if (existing != null && existing.title() != null) {
                continue; // already have this job with a proper title
            }
            if (!looksLikeTitle(text)) {
                if (existing == null) {
                    jobs.put(job.key(), new ParsedJob(job.source(), null, null, null, job.url()));
                }
                continue;
            }
            List<String> lines = cardLines(card(anchor, doc));
            String[] companyAndLocation = companyAndLocation(lines, text);
            jobs.put(job.key(), new ParsedJob(job.source(), text, companyAndLocation[0], companyAndLocation[1], job.url()));
        }

        List<ParsedJob> result = new ArrayList<>();
        for (ParsedJob job : jobs.values()) {
            String title = job.title() != null ? job.title() : titleFromUrl(job);
            if (title != null) {
                result.add(new ParsedJob(job.source(), cut(title, 300), cut(job.company(), 200), cut(job.location(), 200), job.url()));
            }
        }
        return result;
    }

    /** Recognises job links, including ones wrapped in a tracking redirect, and builds a clean URL. */
    static Optional<JobLink> jobLink(String href) {
        if (href == null || href.isBlank()) {
            return Optional.empty();
        }
        String decoded = href;
        try {
            // Tracking redirects carry the real link URL-encoded, sometimes twice
            for (int i = 0; i < 2 && decoded.contains("%"); i++) {
                decoded = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
            }
        } catch (IllegalArgumentException ignored) {
            decoded = href;
        }
        Matcher linkedIn = LINKEDIN_JOB.matcher(decoded);
        if (linkedIn.find()) {
            String id = linkedIn.group(1);
            return Optional.of(new JobLink(Source.LINKEDIN, "li-" + id, "https://www.linkedin.com/jobs/view/" + id + "/"));
        }
        Matcher naukri = NAUKRI_JOB.matcher(decoded.toLowerCase(Locale.ROOT));
        if (naukri.find()) {
            String slug = naukri.group(1);
            return Optional.of(new JobLink(Source.NAUKRI, "nk-" + slug, "https://www.naukri.com/" + slug));
        }
        Matcher indeed = INDEED_JOB.matcher(decoded.toLowerCase(Locale.ROOT));
        if (indeed.find()) {
            String key = indeed.group(1);
            return Optional.of(new JobLink(Source.INDEED, "in-" + key, "https://in.indeed.com/viewjob?jk=" + key));
        }
        return Optional.empty();
    }

    /** The smallest block around the link that holds only this one job. */
    private Element card(Element anchor, Document doc) {
        Optional<JobLink> own = jobLink(anchor.attr("href"));
        String key = own.map(JobLink::key).orElse("");
        Element card = anchor;
        Element parent = anchor.parent();
        for (int depth = 0; parent != null && parent != doc.body() && depth < 8; depth++) {
            boolean otherJobInside = parent.select("a[href]").stream()
                    .map(a -> jobLink(a.attr("href")))
                    .anyMatch(l -> l.isPresent() && !l.get().key().equals(key));
            if (otherJobInside || parent.text().length() > 800) {
                break;
            }
            card = parent;
            parent = parent.parent();
        }
        return card;
    }

    private static List<String> cardLines(Element card) {
        List<String> lines = new ArrayList<>();
        for (Element el : card.getAllElements()) {
            String own = tidy(el.ownText());
            if (!own.isEmpty() && (lines.isEmpty() || !lines.get(lines.size() - 1).equals(own))) {
                lines.add(own);
            }
        }
        return lines;
    }

    /** Company and location come right after the title in both LinkedIn and Naukri cards. */
    private static String[] companyAndLocation(List<String> lines, String title) {
        int start = lines.indexOf(title);
        List<String> after = start >= 0 ? lines.subList(start + 1, lines.size()) : lines;
        String company = null;
        String location = null;
        for (String line : after) {
            if (line.equalsIgnoreCase(title) || NOISE.matcher(line).matches() || NOT_TITLES.contains(line.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (line.contains("\u00b7")) {
                // LinkedIn: "Infosys · Bengaluru, Karnataka, India (On-site)"
                String[] parts = line.split("\\s*\u00b7\\s*", 2);
                if (company == null) {
                    company = parts[0];
                    location = parts.length > 1 ? parts[1] : null;
                } else if (location == null) {
                    location = parts[0];
                }
            } else if (EXPERIENCE.matcher(line).find() || SALARY.matcher(line).find()) {
                continue;
            } else if (company == null) {
                company = line;
            } else if (location == null) {
                location = line;
            }
            if (company != null && location != null) {
                break;
            }
        }
        return new String[]{company, location};
    }

    /** "job-listings-java-developer-infosys-bengaluru-0-to-2-years-150925500123" -> "Java Developer Infosys Bengaluru". */
    private static String titleFromUrl(ParsedJob job) {
        if (job.source() != Source.NAUKRI) {
            return null;
        }
        String slug = job.url().substring(job.url().lastIndexOf('/') + 1)
                .replaceFirst("^job-listings-", "")
                .replaceFirst("-\\d{6,}$", "")
                .replaceAll("-\\d+-to-\\d+-years?", "");
        StringBuilder title = new StringBuilder();
        for (String word : slug.split("-")) {
            if (!word.isBlank()) {
                title.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
            }
        }
        String result = title.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private static boolean looksLikeTitle(String text) {
        return text.length() >= 3 && text.length() <= 150 && text.chars().anyMatch(Character::isLetter)
                && !NOT_TITLES.contains(text.toLowerCase(Locale.ROOT)) && !NOISE.matcher(text).matches();
    }

    private static String tidy(String s) {
        return s == null ? "" : s.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
