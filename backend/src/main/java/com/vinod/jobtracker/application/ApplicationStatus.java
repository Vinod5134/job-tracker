package com.vinod.jobtracker.application;

/**
 * Board columns, in pipeline order.
 */
public enum ApplicationStatus {
    SAVED(0, "Saved"),
    APPLIED(1, "Applied"),
    INTERVIEW(2, "Interview"),
    OFFER(3, "Offer"),
    REJECTED(4, "Rejected");

    private final int rank;
    private final String label;

    ApplicationStatus(int rank, String label) {
        this.rank = rank;
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * Rules for changes detected from emails (manual changes are always allowed):
     * - Offer and Rejected are final, an email never overrides them.
     * - A rejection can happen at any earlier stage.
     * - Otherwise only move forward, so an old "application received" email
     *   can't pull a card back from Interview to Applied.
     */
    public boolean canAutoMoveTo(ApplicationStatus next) {
        if (next == this || this == OFFER || this == REJECTED) {
            return false;
        }
        if (next == REJECTED) {
            return true;
        }
        return next.rank > this.rank;
    }
}
