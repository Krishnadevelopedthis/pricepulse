package com.pricepulse.dto;

import com.pricepulse.model.TrackingStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** PATCH semantics: only non-null fields change. clearTarget=true removes the target price. */
public record UpdateRequest(
        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal targetPrice,
        Boolean clearTarget,
        TrackingStatus status,
        @Size(min = 1, max = 300) String name) {}
