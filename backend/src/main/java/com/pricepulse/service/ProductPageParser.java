package com.pricepulse.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pricepulse.util.PriceParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Server-side extraction. Only structured data is trusted here (JSON-LD, Open Graph / itemprop):
 * pages that render prices with JavaScript cannot be read by the server and will report FAILED.
 */
@Component
public class ProductPageParser {
    private final ObjectMapper mapper;

    public ProductPageParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<ExtractedProduct> parse(String html, String pageUrl) {
        Document doc = Jsoup.parse(html, pageUrl);
        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                JsonNode root = mapper.readTree(script.data());
                Optional<ExtractedProduct> found = fromJsonLd(root, 0);
                if (found.isPresent()) return found;
            } catch (JsonProcessingException ignored) {
                // malformed JSON-LD is common; try the next block
            }
        }
        return fromMeta(doc);
    }

    private Optional<ExtractedProduct> fromJsonLd(JsonNode node, int depth) {
        if (node == null || depth > 6) return Optional.empty();
        if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<ExtractedProduct> r = fromJsonLd(child, depth + 1);
                if (r.isPresent()) return r;
            }
            return Optional.empty();
        }
        if (!node.isObject()) return Optional.empty();
        if (node.has("@graph")) {
            Optional<ExtractedProduct> r = fromJsonLd(node.get("@graph"), depth + 1);
            if (r.isPresent()) return r;
        }
        if (isProduct(node)) {
            String name = text(node.get("name"));
            Optional<ExtractedProduct> offer = fromOffers(node.get("offers"), name);
            if (offer.isPresent()) return offer;
        }
        if (node.has("mainEntity")) return fromJsonLd(node.get("mainEntity"), depth + 1);
        return Optional.empty();
    }

    private boolean isProduct(JsonNode node) {
        JsonNode type = node.get("@type");
        if (type == null) return false;
        if (type.isArray()) {
            for (JsonNode t : type) if ("Product".equalsIgnoreCase(t.asText())) return true;
            return false;
        }
        return "Product".equalsIgnoreCase(type.asText());
    }

    private Optional<ExtractedProduct> fromOffers(JsonNode offers, String name) {
        if (offers == null) return Optional.empty();
        if (offers.isArray()) {
            for (JsonNode o : offers) {
                Optional<ExtractedProduct> r = fromOffers(o, name);
                if (r.isPresent()) return r;
            }
            return Optional.empty();
        }
        String raw = text(offers.get("price"));
        if (raw == null) raw = text(offers.get("lowPrice"));
        if (raw == null && offers.has("priceSpecification")) raw = text(offers.get("priceSpecification").get("price"));
        Optional<BigDecimal> price = PriceParser.parseMachineDecimal(raw);
        if (price.isEmpty()) return Optional.empty();
        String cur = text(offers.get("priceCurrency"));
        if (cur == null && offers.has("priceSpecification")) cur = text(offers.get("priceSpecification").get("priceCurrency"));
        if (cur == null) cur = PriceParser.detectCurrency(raw).orElse(null);
        if (cur == null) return Optional.empty(); // no currency, no guess
        return Optional.of(new ExtractedProduct(name, price.get(), cur.toUpperCase(), "json-ld"));
    }

    private Optional<ExtractedProduct> fromMeta(Document doc) {
        String amount = firstContent(doc, "meta[property=product:price:amount]", "meta[property=og:price:amount]",
                "meta[itemprop=price]", "[itemprop=price][content]");
        String currency = firstContent(doc, "meta[property=product:price:currency]", "meta[property=og:price:currency]",
                "meta[itemprop=priceCurrency]", "[itemprop=priceCurrency][content]");
        Optional<BigDecimal> price = PriceParser.parseMachineDecimal(amount);
        if (price.isEmpty() || currency == null) return Optional.empty();
        String name = firstContent(doc, "meta[property=og:title]");
        return Optional.of(new ExtractedProduct(name, price.get(), currency.toUpperCase(), "meta"));
    }

    private static String firstContent(Document doc, String... selectors) {
        for (String s : selectors) {
            Element e = doc.selectFirst(s);
            if (e != null && !e.attr("content").isBlank()) return e.attr("content").trim();
        }
        return null;
    }

    private static String text(JsonNode n) {
        return (n == null || n.isNull() || n.isContainerNode()) ? null : n.asText().trim();
    }
}
