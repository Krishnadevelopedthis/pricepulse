package com.pricepulse.service;

import com.pricepulse.model.ObservationSource;
import com.pricepulse.model.TrackedProduct;
import com.pricepulse.repo.TrackedProductRepository;
import com.pricepulse.util.InvalidPriceException;
import com.pricepulse.web.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Optional;

/** Performs one server-side price check: fetch, extract, validate, record. Safe to run on worker threads. */
@Service
public class PriceCheckService {
    private static final Logger log = LoggerFactory.getLogger(PriceCheckService.class);

    public enum Outcome { UPDATED, FAILED, SKIPPED }

    public record Result(Outcome outcome, String message) {}

    private final TrackedProductRepository products;
    private final PageFetcher fetcher;
    private final ProductPageParser parser;
    private final ProductService productService;

    public PriceCheckService(TrackedProductRepository products, PageFetcher fetcher,
                             ProductPageParser parser, ProductService productService) {
        this.products = products;
        this.fetcher = fetcher;
        this.parser = parser;
        this.productService = productService;
    }

    public Result check(String productId) {
        Optional<TrackedProduct> found = products.findById(productId);
        if (found.isEmpty()) return new Result(Outcome.SKIPPED, "Product no longer exists.");
        TrackedProduct p = found.get();
        try {
            String html = fetcher.fetch(URI.create(p.getNormalizedUrl()));
            Optional<ExtractedProduct> extracted = parser.parse(html, p.getNormalizedUrl());
            if (extracted.isEmpty()) {
                productService.recordFailure(productId,
                        "No structured price found on the page. It may render prices with JavaScript.");
                return new Result(Outcome.FAILED, "No structured price found on the page.");
            }
            ExtractedProduct e = extracted.get();
            productService.recordObservation(productId, e.price(), e.currency(), ObservationSource.SERVER);
            return new Result(Outcome.UPDATED, "Price read from " + e.method() + ".");
        } catch (InvalidPriceException | ApiException e) {
            productService.recordFailure(productId, e.getMessage());
            return new Result(Outcome.FAILED, e.getMessage());
        } catch (java.io.IOException e) {
            productService.recordFailure(productId, "Could not fetch the page: " + e.getMessage());
            return new Result(Outcome.FAILED, "Could not fetch the page: " + e.getMessage());
        } catch (RuntimeException e) {
            log.warn("Unexpected error checking product {}", productId, e);
            productService.recordFailure(productId, "Unexpected error during the check.");
            return new Result(Outcome.FAILED, "Unexpected error during the check.");
        }
    }
}
