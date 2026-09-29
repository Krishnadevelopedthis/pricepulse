package com.pricepulse.dto;

import com.pricepulse.model.ExtractionStatus;
import com.pricepulse.model.TrackedProduct;
import com.pricepulse.model.TrackingStatus;
import com.pricepulse.util.PriceChangeCalculator.Direction;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        String id, String name, String url, String domain,
        BigDecimal currentPrice, BigDecimal previousPrice, BigDecimal targetPrice, String currency,
        TrackingStatus status, Direction direction, BigDecimal changeAmount, BigDecimal changePercent,
        boolean targetReached, ExtractionStatus extractionStatus, String lastError,
        Instant createdAt, Instant updatedAt, Instant lastCheckedAt, Instant lastSuccessfulCheckAt) {

    public static ProductResponse from(TrackedProduct p) {
        boolean reached = p.getTargetPrice() != null && p.getCurrentPrice() != null
                && p.getCurrentPrice().compareTo(p.getTargetPrice()) <= 0;
        return new ProductResponse(p.getId(), p.getName(), p.getUrl(), p.getDomain(),
                p.getCurrentPrice(), p.getPreviousPrice(), p.getTargetPrice(), p.getCurrency(),
                p.getStatus(), p.getDirection(), p.getChangeAmount(), p.getChangePercent(),
                reached, p.getExtractionStatus(), p.getLastError(),
                p.getCreatedAt(), p.getUpdatedAt(), p.getLastCheckedAt(), p.getLastSuccessfulCheckAt());
    }
}
