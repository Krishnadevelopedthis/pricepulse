package com.pricepulse.service;

import java.math.BigDecimal;

/** Result of parsing a product page. method describes where the price came from. */
public record ExtractedProduct(String name, BigDecimal price, String currency, String method) {}
