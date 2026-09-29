package com.pricepulse.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Computes price movement between two observations. */
public final class PriceChangeCalculator {
    public enum Direction { INCREASED, DECREASED, UNCHANGED }

    /** amount and percent are signed: negative means the price fell. */
    public record Change(Direction direction, BigDecimal amount, BigDecimal percent) {}

    private PriceChangeCalculator() {}

    public static Change compute(BigDecimal previous, BigDecimal current) {
        if (current == null) throw new IllegalArgumentException("current price is required");
        if (previous == null || previous.signum() <= 0) {
            return new Change(Direction.UNCHANGED, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
        }
        int cmp = current.compareTo(previous);
        if (cmp == 0) {
            return new Change(Direction.UNCHANGED, BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2));
        }
        BigDecimal amount = current.subtract(previous).setScale(2, RoundingMode.HALF_UP);
        BigDecimal percent = current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 2, RoundingMode.HALF_UP);
        return new Change(cmp < 0 ? Direction.DECREASED : Direction.INCREASED, amount, percent);
    }
}
