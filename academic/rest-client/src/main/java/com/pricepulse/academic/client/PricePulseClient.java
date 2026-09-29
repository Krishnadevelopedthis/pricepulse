package com.pricepulse.academic.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Java HTTP client (java.net.http) consuming the PricePulse REST API, with Jackson for JSON.
 * Run against a running backend:  mvn -q compile exec:java -Dexec.args="http://localhost:8080"
 */
public class PricePulseClient {
    /** Java object -> JSON (request body). */
    public record TrackRequest(String url, String name, BigDecimal price, String currency, BigDecimal targetPrice) {}

    /** JSON -> Java object (response body). Unknown fields are ignored so the API can grow. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record Product(String id, String name, String domain, BigDecimal currentPrice, BigDecimal targetPrice, String currency, String direction) {}

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule()).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final String base, clientId;

    public PricePulseClient(String base, String clientId) {
        this.base = base.replaceAll("/+$", "");
        this.clientId = clientId;
    }

    public JsonNode health() throws IOException, InterruptedException {                       // GET
        return json.readTree(send(HttpRequest.newBuilder(URI.create(base + "/api/health")).GET()).body());
    }

    public Product track(TrackRequest body) throws IOException, InterruptedException {        // POST
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + "/api/products"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        HttpResponse<String> res = send(b);
        if (res.statusCode() != 201) throw new IOException("Unexpected status " + res.statusCode() + ": " + res.body());
        return json.readValue(res.body(), Product.class);
    }

    public Product[] list() throws IOException, InterruptedException {                        // GET list
        HttpResponse<String> res = send(HttpRequest.newBuilder(URI.create(base + "/api/products")).GET());
        return json.readValue(res.body(), Product[].class);
    }

    public void delete(String id) throws IOException, InterruptedException {                  // DELETE
        send(HttpRequest.newBuilder(URI.create(base + "/api/products/" + id)).DELETE());
    }

    private HttpResponse<String> send(HttpRequest.Builder b) throws IOException, InterruptedException {
        HttpRequest req = b.header("X-Client-Id", clientId).timeout(Duration.ofSeconds(10)).build();
        return http.send(req, HttpResponse.BodyHandlers.ofString());
    }

    public static void main(String[] args) throws Exception {
        PricePulseClient c = new PricePulseClient(args.length > 0 ? args[0] : "http://localhost:8080", UUID.randomUUID().toString());
        System.out.println("health : " + c.health());
        Product p = c.track(new TrackRequest("https://shop.example.com/p/1?utm_source=x", "Demo product",
                new BigDecimal("3499.00"), "INR", new BigDecimal("2999.00")));
        System.out.println("tracked: " + p);
        System.out.println("list   : " + c.list().length + " product(s)");
        c.delete(p.id());
        System.out.println("deleted: " + p.id());
    }
}
