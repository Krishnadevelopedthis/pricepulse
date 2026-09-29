package com.pricepulse.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AckRequest(@NotNull @Size(max = 200) List<String> productIds) {}
