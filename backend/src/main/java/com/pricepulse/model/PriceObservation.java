package com.pricepulse.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.Instant;

/** One real, successful price observation. Failed extractions are never stored here. */
@Document("price_history")
@CompoundIndex(name = "product_time", def = "{'productId': 1, 'observedAt': 1}")
public class PriceObservation {
    @Id
    private String id;
    private String productId;
    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal price;
    private String currency;
    private Instant observedAt;
    private String domain;
    private ObservationSource source;

    public PriceObservation() {}

    public PriceObservation(String productId, BigDecimal price, String currency, Instant observedAt,
                            String domain, ObservationSource source) {
        this.productId = productId;
        this.price = price;
        this.currency = currency;
        this.observedAt = observedAt;
        this.domain = domain;
        this.source = source;
    }

    public String getId() { return id; }
    public String getProductId() { return productId; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
    public Instant getObservedAt() { return observedAt; }
    public String getDomain() { return domain; }
    public ObservationSource getSource() { return source; }
}
