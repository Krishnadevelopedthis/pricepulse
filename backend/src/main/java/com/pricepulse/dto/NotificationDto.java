package com.pricepulse.dto;

import com.pricepulse.model.NotificationType;

import java.math.BigDecimal;

public record NotificationDto(String productId, String name, String url, NotificationType type,
                              BigDecimal price, BigDecimal previousPrice, BigDecimal targetPrice, String currency) {}
