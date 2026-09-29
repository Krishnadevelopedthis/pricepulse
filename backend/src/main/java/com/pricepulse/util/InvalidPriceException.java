package com.pricepulse.util;

/** Thrown when a price, currency or URL fails validation. Framework-free on purpose. */
public class InvalidPriceException extends IllegalArgumentException {
    private final String field;

    public InvalidPriceException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
