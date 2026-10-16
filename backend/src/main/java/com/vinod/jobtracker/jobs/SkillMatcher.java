package com.vinod.jobtracker.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Scores a job description against the user's skills.
 * Uses word boundaries so "Java" doesn't match "JavaScript".
 */
public final class SkillMatcher {

    private static final Pattern FRESHER = Pattern.compile(
            "\\b(fresher|freshers|entry[- ]level|graduate|trainee|0\\s*-\\s*[12]\\s*(yrs?|years?)|"
                    + "0\\s*to\\s*[12]\\s*(yrs?|years?)|junior|campus|2023|2024|2025)\\b", Pattern.CASE_INSENSITIVE);

    private SkillMatcher() {
    }

    public record Match(List<String> matched, int score) {
    }

    public static Match match(List<String> skills, String text) {
        if (skills.isEmpty() || text == null || text.isBlank()) {
            return new Match(List.of(), 0);
        }
        List<String> matched = new ArrayList<>();
        for (String skill : skills) {
            if (patternFor(skill).matcher(text).find()) {
                matched.add(skill);
            }
        }
        // Job ads rarely list more than a handful of skills, so score against at most 8
        int denominator = Math.min(skills.size(), 8);
        int score = (int) Math.min(100, Math.round(matched.size() * 100.0 / denominator));
        return new Match(matched, score);
    }

    public static boolean fresherFriendly(String text) {
        return text != null && FRESHER.matcher(text).find();
    }

    private static Pattern patternFor(String skill) {
        String s = skill.trim();
        if (s.equalsIgnoreCase("REST API") || s.equalsIgnoreCase("REST APIs") || s.equalsIgnoreCase("RESTful APIs")) {
            return Pattern.compile("\\brest(ful)?\\b", Pattern.CASE_INSENSITIVE);
        }
        // "Spring Boot" should also match "SpringBoot" and "spring-boot"
        String[] words = s.split("\\s+");
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                regex.append("[\\s-]*");
            }
            regex.append(Pattern.quote(words[i]));
        }
        // \b doesn't work next to symbols like "C++" or "C#", so use look-arounds instead
        return Pattern.compile("(?<![A-Za-z0-9])" + regex + "(?![A-Za-z0-9])", Pattern.CASE_INSENSITIVE);
    }
}
