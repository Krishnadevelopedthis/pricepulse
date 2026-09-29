package com.pricepulse.util;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Server-side price validation. Never trust client input. */
public final class PriceValidator {
    public static final Set<String> SUPPORTED_CURRENCIES =
            Set.of("INR", "USD", "EUR", "GBP", "JPY", "AUD", "CAD", "CHF", "SGD", "AED");
    public static final BigDecimal MAX_PRICE = new BigDecimal("1000000000");

    private static final Pattern PLAIN_DECIMAL = Pattern.compile("^\\d{1,10}(\\.\\d{1,2})?$");

    private PriceValidator() {}

    public static String requireCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new InvalidPriceException("currency", "Currency is required.");
        }
        String c = currency.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_CURRENCIES.contains(c)) {
            throw new InvalidPriceException("currency", "Unsupported currency: " + c);
        }
        return c;
    }

    /** Validates a price: present, positive, bounded, and with precision valid for the currency. */
    public static BigDecimal requirePrice(String field, BigDecimal price, String currency) {
        if (price == null) throw new InvalidPriceException(field, "Price is required.");
        String c = requireCurrency(currency);
        if (price.signum() <= 0) throw new InvalidPriceException(field, "Price must be greater than zero.");
        if (price.compareTo(MAX_PRICE) > 0) throw new InvalidPriceException(field, "Price is unrealistically large.");
        int maxScale = c.equals("JPY") ? 0 : 2;
        if (price.stripTrailingZeros().scale() > maxScale) {
            throw new InvalidPriceException(field, "Too many decimal places for " + c + ".");
        }
        return price.setScale(maxScale, java.math.RoundingMode.UNNECESSARY);
    }

    /** Parses strict user input such as "4999" or "4999.50". Rejects NaN, Infinity, exponents and text. */
    public static BigDecimal parseUserInput(String field, String raw) {
        if (raw == null || raw.isBlank()) throw new InvalidPriceException(field, "Enter a price.");
        String t = raw.trim();
        if (!PLAIN_DECIMAL.matcher(t).matches()) {
            throw new InvalidPriceException(field, "Enter a number such as 4999 or 4999.50.");
        }
        BigDecimal v = new BigDecimal(t);
        if (v.signum() <= 0) throw new InvalidPriceException(field, "Price must be greater than zero.");
        return v;
    }
}
