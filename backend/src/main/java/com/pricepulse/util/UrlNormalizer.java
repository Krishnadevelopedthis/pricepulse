package com.pricepulse.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates and canonicalises product URLs so the same product is never tracked twice. */
public final class UrlNormalizer {
    private static final Pattern TRACKING_PARAM = Pattern.compile(
            "^(utm_.*|gclid|fbclid|msclkid|mc_cid|mc_eid|ref|ref_|tag|_encoding|psc|pd_rd_.*|pf_rd_.*|content-id|qid|sr|th|linkcode|linkid|smid|spm|cmpid)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern AMAZON_HOST = Pattern.compile("(^|\\.)amazon\\.[a-z.]{2,6}$");
    private static final Pattern AMAZON_ASIN = Pattern.compile("/(?:dp|gp/product|gp/aw/d)/([A-Z0-9]{10})(?:[/?]|$)");

    private UrlNormalizer() {}

    public static URI normalize(String raw) {
        if (raw == null || raw.isBlank()) throw new InvalidPriceException("url", "URL is required.");
        String trimmed = raw.trim();
        if (trimmed.length() > 2048) throw new InvalidPriceException("url", "URL is too long.");
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new InvalidPriceException("url", "URL is not valid.");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new InvalidPriceException("url", "Only http and https URLs are supported.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) throw new InvalidPriceException("url", "URL has no host.");
        if (uri.getUserInfo() != null) throw new InvalidPriceException("url", "URLs with embedded credentials are not allowed.");

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        int port = uri.getPort();
        if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) port = -1;

        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (AMAZON_HOST.matcher(host).find()) {
            Matcher m = AMAZON_ASIN.matcher(path);
            if (m.find()) {
                path = "/dp/" + m.group(1);
                return build(scheme, host, port, path, null);
            }
        }
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return build(scheme, host, port, path, cleanQuery(uri.getRawQuery()));
    }

    public static String domain(URI normalized) {
        String h = normalized.getHost();
        return h.startsWith("www.") ? h.substring(4) : h;
    }

    private static String cleanQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) return null;
        List<String> kept = new ArrayList<>();
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) continue;
            String key = pair.contains("=") ? pair.substring(0, pair.indexOf('=')) : pair;
            if (!TRACKING_PARAM.matcher(key).matches()) kept.add(pair);
        }
        if (kept.isEmpty()) return null;
        kept.sort(String::compareTo);
        return String.join("&", kept);
    }

    private static URI build(String scheme, String host, int port, String path, String query) {
        StringBuilder sb = new StringBuilder(scheme).append("://").append(host);
        if (port != -1) sb.append(':').append(port);
        sb.append(path);
        if (query != null) sb.append('?').append(query);
        return URI.create(sb.toString());
    }
}
