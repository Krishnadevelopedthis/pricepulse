package com.pricepulse.util;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns display strings ("₹1,29,999.00", "1.299,50 €") into BigDecimal and detects currencies.
 * Mirrors extension/shared/pricecore.js; both are exercised with the same case table.
 */
public final class PriceParser {
    private static final Pattern MACHINE_DECIMAL = Pattern.compile("^\\d{1,12}(\\.\\d{1,4})?$");
    private static final Pattern AMOUNT_TOKEN = Pattern.compile("\\d[\\d.,\\u00A0\\u202F' ]*\\d|\\d");

    private static final Map<String, String> SYMBOLS = Map.of(
            "₹", "INR", "$", "USD", "€", "EUR", "£", "GBP", "¥", "JPY");
    private static final Pattern ISO = Pattern.compile("\\b(INR|USD|EUR|GBP|JPY|AUD|CAD|CHF|SGD|AED)\\b");
    private static final Pattern RS = Pattern.compile("\\b(?:Rs\\.?|INR)\\s*(?=\\d)", Pattern.CASE_INSENSITIVE);

    private PriceParser() {}

    /** Detects a currency code from a string containing a symbol or ISO code. */
    public static Optional<String> detectCurrency(String text) {
        if (text == null) return Optional.empty();
        for (Map.Entry<String, String> e : SYMBOLS.entrySet()) {
            if (text.contains(e.getKey())) return Optional.of(e.getValue());
        }
        Matcher iso = ISO.matcher(text.toUpperCase(Locale.ROOT));
        if (iso.find()) return Optional.of(iso.group(1));
        if (RS.matcher(text).find()) return Optional.of("INR");
        return Optional.empty();
    }

    /** Parses a human-formatted amount. Returns empty when no amount can be read. */
    public static Optional<BigDecimal> parseAmount(String text) {
        if (text == null) return Optional.empty();
        Matcher m = AMOUNT_TOKEN.matcher(text);
        if (!m.find()) return Optional.empty();
        String token = m.group().replaceAll("[\\u00A0\\u202F' ]", "");
        int lastDot = token.lastIndexOf('.');
        int lastComma = token.lastIndexOf(',');
        String normalized;
        if (lastDot >= 0 && lastComma >= 0) {
            // Both present: the right-most separator is the decimal separator.
            char dec = lastDot > lastComma ? '.' : ',';
            char grp = dec == '.' ? ',' : '.';
            normalized = token.replace(String.valueOf(grp), "").replace(dec, '.');
        } else if (lastDot >= 0 || lastComma >= 0) {
            char sep = lastDot >= 0 ? '.' : ',';
            int count = token.length() - token.replace(String.valueOf(sep), "").length();
            int digitsAfter = token.length() - token.lastIndexOf(sep) - 1;
            if (count > 1 || digitsAfter == 3) {
                normalized = token.replace(String.valueOf(sep), ""); // thousands separator
            } else {
                normalized = token.replace(sep, '.');
            }
        } else {
            normalized = token;
        }
        try {
            return Optional.of(new BigDecimal(normalized));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    /**
     * Parses prices from machine-readable sources (JSON-LD, meta tags) where "." is the decimal
     * separator per schema.org. Falls back to {@link #parseAmount(String)} for formatted strings.
     */
    public static Optional<BigDecimal> parseMachineDecimal(String text) {
        if (text == null) return Optional.empty();
        String t = text.trim();
        if (MACHINE_DECIMAL.matcher(t).matches()) return Optional.of(new BigDecimal(t));
        return parseAmount(t);
    }
}
