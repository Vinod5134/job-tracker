package com.vinod.jobtracker.application;

import java.util.Locale;
import java.util.Set;

/**
 * Matches company names written differently, e.g. "Infosys Ltd" and "Infosys Limited",
 * or "Tech Mahindra" and "Tech Mahindra Pvt. Ltd.".
 */
public final class CompanyNames {

    private static final Set<String> SUFFIXES = Set.of(
            "pvt", "private", "ltd", "limited", "inc", "llp", "llc", "corp", "corporation",
            "co", "company", "india", "technologies", "technology", "solutions", "services",
            "software", "systems", "consulting", "global", "group", "the");

    private CompanyNames() {
    }

    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String[] words = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", " ").trim().split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty() && !SUFFIXES.contains(word)) {
                out.append(word).append(' ');
            }
        }
        String result = out.toString().trim();
        // Names made only of suffix words (e.g. "Global Solutions") fall back to the plain form
        return result.isEmpty() ? String.join(" ", words) : result;
    }

    public static boolean sameCompany(String a, String b) {
        String na = normalize(a);
        String nb = normalize(b);
        if (na.isEmpty() || nb.isEmpty()) {
            return false;
        }
        if (na.equals(nb)) {
            return true;
        }
        // "infosys" vs "infosys bpm" style matches, but not for very short names
        String shorter = na.length() < nb.length() ? na : nb;
        String longer = na.length() < nb.length() ? nb : na;
        return shorter.length() >= 4 && (longer.startsWith(shorter + " ") || longer.endsWith(" " + shorter));
    }
}
