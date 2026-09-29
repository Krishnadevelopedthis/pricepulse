package com.pricepulse.service;

import com.pricepulse.config.PricePulseProperties;
import com.pricepulse.util.UrlSafety;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** Fetches product pages with timeouts, bounded retries + exponential backoff, and an SSRF guard. */
@Component
public class PageFetcher {
    private static final Logger log = LoggerFactory.getLogger(PageFetcher.class);
    private final PricePulseProperties.Fetch cfg;
    private final HttpClient client;

    public PageFetcher(PricePulseProperties props) {
        this.cfg = props.fetch();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(cfg.connectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER) // redirects are followed manually so every hop is checked
                .build();
    }

    public String fetch(URI uri) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < cfg.maxAttempts(); attempt++) {
            try {
                return fetchOnce(uri);
            } catch (RetryableException e) {
                last = new IOException(e.getMessage());
                sleepBackoff(attempt);
            } catch (IOException e) {
                last = e;
                if (e instanceof PermanentException) throw e;
                sleepBackoff(attempt);
            }
        }
        throw last != null ? last : new IOException("Fetch failed");
    }

    private String fetchOnce(URI start) throws IOException {
        URI current = start;
        for (int hop = 0; hop <= cfg.maxRedirects(); hop++) {
            if (!cfg.allowPrivateHosts()) UrlSafety.assertPublic(current);
            HttpRequest req = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofMillis(cfg.requestTimeoutMs()))
                    .header("User-Agent", "Mozilla/5.0 (compatible; PricePulseBot/1.0)")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET().build();
            HttpResponse<InputStream> res;
            try {
                res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new PermanentException("Interrupted");
            }
            int code = res.statusCode();
            if (code >= 300 && code < 400) {
                String loc = res.headers().firstValue("Location").orElse(null);
                res.body().close();
                if (loc == null) throw new PermanentException("Redirect without Location");
                current = current.resolve(loc);
                if (!"http".equals(current.getScheme()) && !"https".equals(current.getScheme())) {
                    throw new PermanentException("Redirect to unsupported scheme");
                }
                continue;
            }
            if (code == 429 || code >= 500) {
                res.body().close();
                throw new RetryableException("HTTP " + code);
            }
            if (code >= 400) {
                res.body().close();
                throw new PermanentException("HTTP " + code + " (site may block automated requests)");
            }
            try (InputStream in = res.body()) {
                return new String(in.readNBytes(cfg.maxBodyBytes()), StandardCharsets.UTF_8);
            }
        }
        throw new PermanentException("Too many redirects");
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(400L * (1L << attempt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class RetryableException extends IOException {
        RetryableException(String m) { super(m); }
    }

    private static class PermanentException extends IOException {
        PermanentException(String m) { super(m); }
    }
}
