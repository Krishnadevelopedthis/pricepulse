package com.pricepulse.service;

import com.pricepulse.config.PricePulseProperties;
import com.pricepulse.model.TrackedProduct;
import com.pricepulse.model.TrackingStatus;
import com.pricepulse.repo.TrackedProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scheduler -> due products -> bounded executor -> price check tasks -> MongoDB.
 * Runs on the server, so it keeps working while Chrome is closed and the popup is never needed.
 */
@Component
public class PriceCheckScheduler {
    private static final Logger log = LoggerFactory.getLogger(PriceCheckScheduler.class);

    private final TrackedProductRepository products;
    private final PriceCheckService checker;
    private final ExecutorService executor;
    private final PricePulseProperties props;

    public PriceCheckScheduler(TrackedProductRepository products, PriceCheckService checker,
                               ExecutorService priceCheckExecutor, PricePulseProperties props) {
        this.products = products;
        this.checker = checker;
        this.executor = priceCheckExecutor;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${pricepulse.scheduler.tick-ms}", initialDelayString = "${pricepulse.scheduler.tick-ms}")
    public void tick() {
        if (!props.scheduler().enabled()) return;
        List<TrackedProduct> due = products.findByStatusAndNextCheckAtLessThanEqual(
                TrackingStatus.ACTIVE, Instant.now(), PageRequest.of(0, props.scheduler().batchSize()));
        if (due.isEmpty()) return;

        AtomicInteger updated = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        List<CompletableFuture<Void>> futures = due.stream()
                .map(p -> CompletableFuture
                        .supplyAsync(() -> checker.check(p.getId()), executor)
                        .orTimeout(60, TimeUnit.SECONDS)
                        .handle((result, error) -> {
                            if (error == null && result.outcome() == PriceCheckService.Outcome.UPDATED) updated.incrementAndGet();
                            else failed.incrementAndGet();
                            return (Void) null;
                        }))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.info("Price check batch finished: {} due, {} updated, {} failed/skipped", due.size(), updated.get(), failed.get());
    }
}
