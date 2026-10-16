package com.vinod.jobtracker.gmail;

import java.time.Instant;

public record GmailStatus(boolean connected, String email, Instant lastSyncedAt, String lastError, long needsReview) {

    public static GmailStatus notConnected() {
        return new GmailStatus(false, null, null, null, 0);
    }
}
