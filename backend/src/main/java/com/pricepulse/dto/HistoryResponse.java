package com.pricepulse.dto;

import com.pricepulse.model.ObservationSource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record HistoryResponse(String productId, String currency, List<Point> points, Stats stats) {
    public record Point(Instant observedAt, BigDecimal price, ObservationSource source) {}

    /** All values are derived from stored observations only. Null when there are no observations. */
    public record Stats(int count, BigDecimal current, BigDecimal previous, BigDecimal lowest,
                        BigDecimal highest, BigDecimal first, Instant stableSince) {}
}
