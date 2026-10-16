package com.vinod.jobtracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of the "app.*" settings in application.yml.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String frontendUrl,
        String jwtSecret,
        int jwtExpiryHours,
        String encryptionKey,
        Gmail gmail,
        Adzuna adzuna,
        String defaultSkills) {

    public AppProperties {
        // "https://x.vercel.app/" and "https://x.vercel.app" must behave the same (CORS is an exact match)
        if (frontendUrl != null && frontendUrl.endsWith("/")) {
            frontendUrl = frontendUrl.substring(0, frontendUrl.length() - 1);
        }
        if (jwtSecret == null || jwtSecret.length() < 32) {
            throw new IllegalStateException("app.jwt-secret (JWT_SECRET) must be at least 32 characters");
        }
    }

    public record Gmail(long syncIntervalMs, int firstSyncDays, int maxMessagesPerSync) {
    }

    public record Adzuna(String appId, String appKey, String country) {
        public boolean configured() {
            return appId != null && !appId.isBlank() && appKey != null && !appKey.isBlank();
        }
    }
}
