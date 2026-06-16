package com.ontheway.model.enums;

/**
 * Store vertical / category. OnTheWay is not food-only — the same pre-order +
 * ETA-synced pickup primitive serves many verticals. New categories can be added here
 * (persisted as strings, so no migration is required).
 */
public enum StoreType {
    RESTAURANT,
    CAFE,
    PHARMACY,
    GROCERY,
    BAKERY,
    RETAIL,
    ELECTRONICS,
    FLORIST,
    OTHER
}