package com.ontheway.model.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle states of an order and the legal transitions between them.
 *
 * <pre>
 *   PLACED ──▶ ACCEPTED ──▶ PREPARING ──▶ READY ──▶ PICKED
 *      │             │              │
 *      └─────────────┴──────────────┴────────▶ CANCELLED
 * </pre>
 */
public enum OrderStatus {
    /** Payment is recorded and the shop is reviewing the request. */
    PLACED,
    /** The shop has committed to the pickup window; live route and chat are now active. */
    ACCEPTED,
    PREPARING,
    READY,
    PICKED,
    CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            PLACED,    EnumSet.of(ACCEPTED, CANCELLED),
            ACCEPTED,  EnumSet.of(PREPARING, CANCELLED),
            PREPARING, EnumSet.of(READY, CANCELLED),
            READY,     EnumSet.of(PICKED),
            PICKED,    EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class)
    );

    /** Returns true if moving from this status to {@code next} is a legal transition. */
    public boolean canTransitionTo(OrderStatus next) {
        return next != null && ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }

    public boolean isTerminal() {
        return this == PICKED || this == CANCELLED;
    }
}
