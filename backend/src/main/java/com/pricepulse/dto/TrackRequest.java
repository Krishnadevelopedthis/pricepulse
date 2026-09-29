package com.pricepulse.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record TrackRequest(
        @NotBlank @Size(max = 2048) String url,
        @NotBlank @Size(max = 300) String name,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$") String currency,
        @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal targetPrice) {}
