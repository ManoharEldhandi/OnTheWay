package com.ontheway.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    public static final String DEFAULT_CURRENCY = "INR";

    private Money() {}

    public static long toMinor(double major) {
        return BigDecimal.valueOf(major).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public static double toMajor(long minor) {
        return BigDecimal.valueOf(minor, 2).doubleValue();
    }
}
