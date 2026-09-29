package com.pricepulse.academic.jdbc;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceRecord(long id, String productId, BigDecimal price, String currency, Instant observedAt, String source) {}
