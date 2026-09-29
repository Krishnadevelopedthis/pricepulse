package com.pricepulse.model;

import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

/** Embedded in TrackedProduct. Prevents notification spam. */
public class NotificationState {
    private NotificationType pendingType;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal pendingPrice;
    private Instant pendingAt;
    /** True once the target alert fired; re-armed when the price rises back above target. */
    private boolean targetAlertSent;
    private Instant lastNotifiedAt;

    public NotificationType getPendingType() { return pendingType; }
    public void setPendingType(NotificationType pendingType) { this.pendingType = pendingType; }
    public BigDecimal getPendingPrice() { return pendingPrice; }
    public void setPendingPrice(BigDecimal pendingPrice) { this.pendingPrice = pendingPrice; }
    public Instant getPendingAt() { return pendingAt; }
    public void setPendingAt(Instant pendingAt) { this.pendingAt = pendingAt; }
    public boolean isTargetAlertSent() { return targetAlertSent; }
    public void setTargetAlertSent(boolean targetAlertSent) { this.targetAlertSent = targetAlertSent; }
    public Instant getLastNotifiedAt() { return lastNotifiedAt; }
    public void setLastNotifiedAt(Instant lastNotifiedAt) { this.lastNotifiedAt = lastNotifiedAt; }
}
