package com.ontheway.model.enums;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle states of an order and the legal transitions between them.
 *
 * <pre>
 *   PLACED ──▶ PREPARING ──▶ READY ──▶ PICKED
 *      │            │
 *      └────────────┴────────▶ CANCELLED
 * </pre>
 */
public enum OrderStatus {
    PLACED, PREPARING, READY, PICKED, CANCELLED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            PLACED,    EnumSet.of(PREPARING, CANCELLED),
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