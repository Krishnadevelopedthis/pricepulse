package com.pricepulse.util;

import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;

/** Dependency-free test runner for the framework-free core logic. Run: see README "Testing". */
public class CoreLogicSelfTest {
    static int pass, fail;

    static void eq(String name, Object expected, Object actual) {
        boolean ok = String.valueOf(expected).equals(String.valueOf(actual));
        if (ok) pass++; else fail++;
        System.out.println((ok ? "PASS " : "FAIL ") + name + (ok ? "" : "  expected=" + expected + " actual=" + actual));
    }

    static void rejects(String name, Runnable r) {
        try { r.run(); fail++; System.out.println("FAIL " + name + " (no exception)"); }
        catch (InvalidPriceException e) { pass++; System.out.println("PASS " + name); }
    }

    static String amt(String s) { return PriceParser.parseAmount(s).map(BigDecimal::toPlainString).orElse("EMPTY"); }

    public static void main(String[] args) throws Exception {
        // Shared table with tests/core.test.mjs
        eq("parse ₹1,299", "1299", amt("₹1,299"));
        eq("parse ₹1,299.00", "1299.00", amt("₹1,299.00"));
        eq("parse $29.99", "29.99", amt("$29.99"));
        eq("parse €49,99", "49.99", amt("€49,99"));
        eq("parse 1.299,50 €", "1299.50", amt("1.299,50 €"));
        eq("parse lakh ₹1,29,999", "129999", amt("₹1,29,999"));
        eq("parse 1 299,00", "1299.00", amt("1 299,00"));
        eq("parse £39.99 with text", "39.99", amt("Price: £39.99 incl. VAT"));
        eq("parse no digits", "EMPTY", amt("Out of stock"));
        eq("machine 1299.5", "1299.5", PriceParser.parseMachineDecimal("1299.5").get().toPlainString());
        eq("machine 1.299 stays decimal", "1.299", PriceParser.parseMachineDecimal("1.299").get().toPlainString());
        eq("currency ₹", "INR", PriceParser.detectCurrency("₹1,299").orElse("?"));
        eq("currency Rs.", "INR", PriceParser.detectCurrency("Rs. 1,299").orElse("?"));
        eq("currency ISO", "EUR", PriceParser.detectCurrency("EUR 20").orElse("?"));
        eq("currency none", "?", PriceParser.detectCurrency("1299").orElse("?"));

        // Validation
        eq("valid price", "4999.50", PriceValidator.requirePrice("price", new BigDecimal("4999.50"), "inr").toPlainString());
        rejects("zero price", () -> PriceValidator.requirePrice("price", BigDecimal.ZERO, "INR"));
        rejects("negative price", () -> PriceValidator.requirePrice("price", new BigDecimal("-1"), "INR"));
        rejects("null price", () -> PriceValidator.requirePrice("price", null, "INR"));
        rejects("3 decimals", () -> PriceValidator.requirePrice("price", new BigDecimal("1.234"), "USD"));
        rejects("JPY decimals", () -> PriceValidator.requirePrice("price", new BigDecimal("10.5"), "JPY"));
        rejects("huge price", () -> PriceValidator.requirePrice("price", new BigDecimal("1e12"), "USD"));
        rejects("bad currency", () -> PriceValidator.requirePrice("price", BigDecimal.TEN, "XXX"));
        eq("user input ok", "4999", PriceValidator.parseUserInput("t", " 4999 ").toPlainString());
        for (String bad : new String[]{"", "NaN", "Infinity", "-5", "1e5", "abc", "12.345", "0", "1,299"}) {
            rejects("user input rejects '" + bad + "'", () -> PriceValidator.parseUserInput("t", bad));
        }

        // Price change
        var down = PriceChangeCalculator.compute(new BigDecimal("3499"), new BigDecimal("3199"));
        eq("decrease direction", "DECREASED", down.direction());
        eq("decrease amount", "-300.00", down.amount().toPlainString());
        eq("decrease percent", "-8.57", down.percent().toPlainString());
        var up = PriceChangeCalculator.compute(new BigDecimal("2999"), new BigDecimal("3499"));
        eq("increase direction", "INCREASED", up.direction());
        eq("increase amount", "500.00", up.amount().toPlainString());
        eq("increase percent", "16.67", up.percent().toPlainString());
        eq("unchanged", "UNCHANGED", PriceChangeCalculator.compute(BigDecimal.TEN, new BigDecimal("10.00")).direction());
        eq("first observation", "UNCHANGED", PriceChangeCalculator.compute(null, BigDecimal.TEN).direction());

        // URL normalisation
        eq("strip tracking+fragment", "https://shop.example.com/p/shoe?color=red",
                UrlNormalizer.normalize("HTTPS://Shop.Example.com:443/p/shoe/?utm_source=x&color=red#reviews"));
        eq("amazon canonical", "https://www.amazon.in/dp/B0ABCDEFGH",
                UrlNormalizer.normalize("https://www.amazon.in/Some-Title/dp/B0ABCDEFGH/ref=sr_1_3?keywords=x&qid=1"));
        eq("domain", "example.com", UrlNormalizer.domain(URI.create("https://www.example.com/x")));
        rejects("ftp url", () -> UrlNormalizer.normalize("ftp://example.com/a"));
        rejects("javascript url", () -> UrlNormalizer.normalize("javascript:alert(1)"));
        rejects("credentials url", () -> UrlNormalizer.normalize("https://user:pw@example.com/a"));
        rejects("empty url", () -> UrlNormalizer.normalize(" "));
        rejects("no host", () -> UrlNormalizer.normalize("https:///path"));

        // SSRF guard
        eq("loopback blocked", true, UrlSafety.isNonPublic(InetAddress.getByName("127.0.0.1")));
        eq("10.x blocked", true, UrlSafety.isNonPublic(InetAddress.getByName("10.1.2.3")));
        eq("192.168 blocked", true, UrlSafety.isNonPublic(InetAddress.getByName("192.168.0.5")));
        eq("metadata IP blocked", true, UrlSafety.isNonPublic(InetAddress.getByName("169.254.169.254")));
        eq("CGNAT blocked", true, UrlSafety.isNonPublic(InetAddress.getByName("100.64.0.1")));
        eq("IPv6 ULA blocked", true, UrlSafety.isNonPublic(InetAddress.getByName("fd00::1")));
        eq("public allowed", false, UrlSafety.isNonPublic(InetAddress.getByName("93.184.216.34")));

        // Rate limiter (fake clock)
        long[] t = {0L};
        RateLimiter rl = new RateLimiter(3, 1000, () -> t[0]);
        eq("rate 1st", true, rl.tryAcquire("a"));
        eq("rate 2nd", true, rl.tryAcquire("a"));
        eq("rate 3rd", true, rl.tryAcquire("a"));
        eq("rate 4th blocked", false, rl.tryAcquire("a"));
        eq("rate other key independent", true, rl.tryAcquire("b"));
        eq("retry-after >= 1", true, rl.retryAfterSeconds("a") >= 1);
        t[0] += 1_100_000_000L;
        eq("rate window resets", true, rl.tryAcquire("a"));

        System.out.println("\nRESULT: " + pass + " passed, " + fail + " failed");
        if (fail > 0) System.exit(1);
    }
}
