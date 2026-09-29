package com.pricepulse.model;

import com.pricepulse.util.PriceChangeCalculator.Direction;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

@Document("tracked_products")
@CompoundIndexes({
        // Prevents tracking the same normalised URL twice for one user.
        @CompoundIndex(name = "uniq_user_url", def = "{'userId': 1, 'normalizedUrl': 1}", unique = true),
        // Supports the scheduler's "what is due?" query.
        @CompoundIndex(name = "status_nextCheck", def = "{'status': 1, 'nextCheckAt': 1}"),
        @CompoundIndex(name = "user_created", def = "{'userId': 1, 'createdAt': -1}")
})
public class TrackedProduct {
    @Id
    private String id;
    private String userId;
    private String name;
    private String url;
    private String normalizedUrl;
    private String domain;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal currentPrice;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal previousPrice;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal targetPrice;
    private String currency;
    private TrackingStatus status = TrackingStatus.ACTIVE;
    private Direction direction = Direction.UNCHANGED;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal changeAmount;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal changePercent;
    private ExtractionStatus extractionStatus = ExtractionStatus.OK;
    private String lastError;
    private int consecutiveFailures;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant lastCheckedAt;
    private Instant lastSuccessfulCheckAt;
    private Instant nextCheckAt;
    private NotificationState notification = new NotificationState();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getNormalizedUrl() { return normalizedUrl; }
    public void setNormalizedUrl(String normalizedUrl) { this.normalizedUrl = normalizedUrl; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public BigDecimal getCurrentPrice() { return currentPrice; }
    public void setCurrentPrice(BigDecimal currentPrice) { this.currentPrice = currentPrice; }
    public BigDecimal getPreviousPrice() { return previousPrice; }
    public void setPreviousPrice(BigDecimal previousPrice) { this.previousPrice = previousPrice; }
    public BigDecimal getTargetPrice() { return targetPrice; }
    public void setTargetPrice(BigDecimal targetPrice) { this.targetPrice = targetPrice; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public TrackingStatus getStatus() { return status; }
    public void setStatus(TrackingStatus status) { this.status = status; }
    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }
    public BigDecimal getChangeAmount() { return changeAmount; }
    public void setChangeAmount(BigDecimal changeAmount) { this.changeAmount = changeAmount; }
    public BigDecimal getChangePercent() { return changePercent; }
    public void setChangePercent(BigDecimal changePercent) { this.changePercent = changePercent; }
    public ExtractionStatus getExtractionStatus() { return extractionStatus; }
    public void setExtractionStatus(ExtractionStatus extractionStatus) { this.extractionStatus = extractionStatus; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public void setConsecutiveFailures(int consecutiveFailures) { this.consecutiveFailures = consecutiveFailures; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Instant getLastCheckedAt() { return lastCheckedAt; }
    public void setLastCheckedAt(Instant lastCheckedAt) { this.lastCheckedAt = lastCheckedAt; }
    public Instant getLastSuccessfulCheckAt() { return lastSuccessfulCheckAt; }
    public void setLastSuccessfulCheckAt(Instant lastSuccessfulCheckAt) { this.lastSuccessfulCheckAt = lastSuccessfulCheckAt; }
    public Instant getNextCheckAt() { return nextCheckAt; }
    public void setNextCheckAt(Instant nextCheckAt) { this.nextCheckAt = nextCheckAt; }
    public NotificationState getNotification() { return notification; }
    public void setNotification(NotificationState notification) { this.notification = notification; }
}
