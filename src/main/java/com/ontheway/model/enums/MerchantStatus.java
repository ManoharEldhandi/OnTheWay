package com.ontheway.model.enums;

/**
 * Lifecycle status of a shop on the marketplace.
 *
 * <pre>
 *   PENDING ──▶ APPROVED ──▶ SUSPENDED ──▶ APPROVED
 *      │
 *      └──────▶ REJECTED
 * </pre>
 *
 * A shop is publicly discoverable and able to accept orders only when {@link #APPROVED}.
 */
public enum MerchantStatus {
    /** Submitted by a merchant, awaiting administrator review. */
    PENDING,
    /** Approved by an administrator; live and discoverable. */
    APPROVED,
    /** Rejected by an administrator (a reason is recorded). */
    REJECTED,
    /** Temporarily disabled by an administrator; hidden from discovery. */
    SUSPENDED;

    /** Returns true when a shop in this status should appear in public discovery. */
    public boolean isPubliclyVisible() {
        return this == APPROVED;
    }
}
