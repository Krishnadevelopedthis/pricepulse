package com.pricepulse.service;

import com.pricepulse.config.PricePulseProperties;
import com.pricepulse.dto.HistoryResponse;
import com.pricepulse.dto.NotificationDto;
import com.pricepulse.dto.TrackRequest;
import com.pricepulse.dto.UpdateRequest;
import com.pricepulse.model.*;
import com.pricepulse.repo.PriceObservationRepository;
import com.pricepulse.repo.TrackedProductRepository;
import com.pricepulse.util.PriceChangeCalculator;
import com.pricepulse.util.PriceChangeCalculator.Change;
import com.pricepulse.util.PriceChangeCalculator.Direction;
import com.pricepulse.util.PriceValidator;
import com.pricepulse.util.UrlNormalizer;
import com.pricepulse.web.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class ProductService {
    private final TrackedProductRepository products;
    private final PriceObservationRepository history;
    private final PricePulseProperties props;

    /**
     * One lock per product id. Browser observations, scheduler checks and manual checks can arrive
     * concurrently; serialising them per product keeps previous/current/direction consistent
     * (read-modify-write on one document) without blocking unrelated products.
     */
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public ProductService(TrackedProductRepository products, PriceObservationRepository history,
                          PricePulseProperties props) {
        this.products = products;
        this.history = history;
        this.props = props;
    }

    // ---------- queries ----------

    public List<TrackedProduct> list(String userId) {
        return products.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public TrackedProduct get(String userId, String id) {
        return products.findByIdAndUserId(id, userId).orElseThrow(ApiException::notFound);
    }

    public TrackedProduct lookup(String userId, String rawUrl) {
        String normalized = UrlNormalizer.normalize(rawUrl).toString();
        return products.findByUserIdAndNormalizedUrl(userId, normalized).orElseThrow(ApiException::notFound);
    }

    // ---------- commands ----------

    public TrackedProduct track(String userId, TrackRequest req) {
        URI normalized = UrlNormalizer.normalize(req.url());
        String currency = PriceValidator.requireCurrency(req.currency());
        BigDecimal price = PriceValidator.requirePrice("price", req.price(), currency);
        BigDecimal target = req.targetPrice() == null ? null
                : PriceValidator.requirePrice("targetPrice", req.targetPrice(), currency);

        var existing = products.findByUserIdAndNormalizedUrl(userId, normalized.toString());
        if (existing.isPresent()) throw duplicate(existing.get().getId());
        if (products.countByUserId(userId) >= props.maxProductsPerUser()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "LIMIT_REACHED",
                    "You can track up to " + props.maxProductsPerUser() + " products.");
        }

        Instant now = Instant.now();
        TrackedProduct p = new TrackedProduct();
        p.setUserId(userId);
        p.setName(req.name().trim());
        p.setUrl(req.url().trim());
        p.setNormalizedUrl(normalized.toString());
        p.setDomain(UrlNormalizer.domain(normalized));
        p.setCurrency(currency);
        p.setTargetPrice(target);
        p.setCurrentPrice(price);
        p.setDirection(Direction.UNCHANGED);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        p.setLastCheckedAt(now);
        p.setLastSuccessfulCheckAt(now);
        p.setNextCheckAt(now.plus(Duration.ofMinutes(props.scheduler().checkIntervalMinutes())));
        // Already at/below target when tracking starts: the user can see that, so don't fire an alert.
        if (target != null && price.compareTo(target) <= 0) p.getNotification().setTargetAlertSent(true);
        try {
            p = products.save(p);
        } catch (DuplicateKeyException e) {
            throw duplicate(products.findByUserIdAndNormalizedUrl(userId, normalized.toString())
                    .map(TrackedProduct::getId).orElse(null));
        }
        history.save(new PriceObservation(p.getId(), price, currency, now, p.getDomain(), ObservationSource.BROWSER_INITIAL));
        return p;
    }

    public TrackedProduct update(String userId, String id, UpdateRequest req) {
        TrackedProduct current = get(userId, id);
        return withLock(current.getId(), () -> {
            TrackedProduct p = get(userId, id);
            if (req.name() != null) p.setName(req.name().trim());
            if (Boolean.TRUE.equals(req.clearTarget())) {
                p.setTargetPrice(null);
                p.getNotification().setTargetAlertSent(false);
            } else if (req.targetPrice() != null) {
                BigDecimal t = PriceValidator.requirePrice("targetPrice", req.targetPrice(), p.getCurrency());
                p.setTargetPrice(t);
                // Re-arm or disarm relative to the new target without firing a stale alert.
                p.getNotification().setTargetAlertSent(p.getCurrentPrice().compareTo(t) <= 0);
            }
            if (req.status() != null) {
                p.setStatus(req.status());
                if (req.status() == TrackingStatus.ACTIVE) p.setNextCheckAt(Instant.now());
            }
            p.setUpdatedAt(Instant.now());
            return products.save(p);
        });
    }

    public void delete(String userId, String id) {
        TrackedProduct p = get(userId, id);
        withLock(p.getId(), () -> {
            products.deleteById(p.getId());
            history.deleteByProductId(p.getId());
            return null;
        });
        locks.remove(p.getId());
    }

    /**
     * Records a validated observation for a product and updates comparison + notification state.
     * Called by the browser-observation endpoint and by server-side checks.
     */
    public TrackedProduct recordObservation(String productId, BigDecimal rawPrice, String rawCurrency,
                                            ObservationSource source) {
        return withLock(productId, () -> {
            TrackedProduct p = products.findById(productId).orElseThrow(ApiException::notFound);
            String currency = PriceValidator.requireCurrency(rawCurrency);
            if (!currency.equals(p.getCurrency())) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "CURRENCY_MISMATCH",
                        "Observed currency " + currency + " differs from tracked currency " + p.getCurrency() + ".");
            }
            BigDecimal price = PriceValidator.requirePrice("price", rawPrice, currency);
            Instant now = Instant.now();

            BigDecimal before = p.getCurrentPrice();
            Change change = PriceChangeCalculator.compute(before, price);
            if (change.direction() != Direction.UNCHANGED) p.setPreviousPrice(before);
            p.setCurrentPrice(price);
            p.setDirection(change.direction());
            p.setChangeAmount(change.amount());
            p.setChangePercent(change.percent());
            p.setExtractionStatus(ExtractionStatus.OK);
            p.setLastError(null);
            p.setConsecutiveFailures(0);
            p.setLastCheckedAt(now);
            p.setLastSuccessfulCheckAt(now);
            p.setUpdatedAt(now);
            p.setNextCheckAt(now.plus(Duration.ofMinutes(props.scheduler().checkIntervalMinutes())));
            updateNotification(p, change.direction(), price, before, now);

            // Only a real change adds a history row; re-reading the same price must not repeat it.
            if (change.direction() != Direction.UNCHANGED) {
                history.save(new PriceObservation(p.getId(), price, currency, now, p.getDomain(), source));
            }
            return products.save(p);
        });
    }

    /** Records a failed extraction (no history row) and schedules a backed-off retry. */
    public TrackedProduct recordFailure(String productId, String message) {
        return withLock(productId, () -> {
            TrackedProduct p = products.findById(productId).orElse(null);
            if (p == null) return null;
            Instant now = Instant.now();
            int failures = p.getConsecutiveFailures() + 1;
            int exp = Math.min(failures, props.scheduler().maxBackoffExponent());
            p.setConsecutiveFailures(failures);
            p.setExtractionStatus(ExtractionStatus.FAILED);
            p.setLastError(message == null ? "Price could not be read." : truncate(message, 200));
            p.setLastCheckedAt(now);
            p.setUpdatedAt(now);
            p.setNextCheckAt(now.plus(Duration.ofMinutes((long) props.scheduler().checkIntervalMinutes() << exp)));
            return products.save(p);
        });
    }

    // ---------- notifications ----------

    public List<NotificationDto> pendingNotifications(String userId) {
        List<NotificationDto> out = new ArrayList<>();
        for (TrackedProduct p : products.findByUserIdAndNotificationPendingTypeNotNull(userId)) {
            NotificationState n = p.getNotification();
            out.add(new NotificationDto(p.getId(), p.getName(), p.getUrl(), n.getPendingType(), n.getPendingPrice(),
                    p.getPreviousPrice(), p.getTargetPrice(), p.getCurrency()));
        }
        return out;
    }

    public void acknowledge(String userId, List<String> productIds) {
        for (String id : productIds) {
            products.findByIdAndUserId(id, userId).ifPresent(owned -> withLock(id, () -> {
                TrackedProduct p = products.findById(id).orElse(null);
                if (p == null) return null;
                NotificationState n = p.getNotification();
                n.setPendingType(null);
                n.setPendingPrice(null);
                n.setPendingAt(null);
                n.setLastNotifiedAt(Instant.now());
                return products.save(p);
            }));
        }
    }

    // ---------- history ----------

    public HistoryResponse history(String userId, String id, int limit) {
        TrackedProduct p = get(userId, id);
        int capped = Math.max(1, Math.min(limit, 1000));
        List<PriceObservation> newestFirst = history.findByProductIdOrderByObservedAtDesc(p.getId(), PageRequest.of(0, capped));
        List<PriceObservation> chrono = new ArrayList<>(newestFirst);
        Collections.reverse(chrono);
        List<HistoryResponse.Point> points = chrono.stream()
                .map(o -> new HistoryResponse.Point(o.getObservedAt(), o.getPrice(), o.getSource())).toList();
        return new HistoryResponse(p.getId(), p.getCurrency(), points, stats(chrono));
    }

    static HistoryResponse.Stats stats(List<PriceObservation> chrono) {
        if (chrono.isEmpty()) return new HistoryResponse.Stats(0, null, null, null, null, null, null);
        BigDecimal low = chrono.get(0).getPrice(), high = low;
        for (PriceObservation o : chrono) {
            if (o.getPrice().compareTo(low) < 0) low = o.getPrice();
            if (o.getPrice().compareTo(high) > 0) high = o.getPrice();
        }
        int last = chrono.size() - 1;
        BigDecimal current = chrono.get(last).getPrice();
        BigDecimal previous = last > 0 ? chrono.get(last - 1).getPrice() : null;
        int i = last;
        while (i > 0 && chrono.get(i - 1).getPrice().compareTo(current) == 0) i--;
        return new HistoryResponse.Stats(chrono.size(), current, previous, low, high,
                chrono.get(0).getPrice(), chrono.get(i).getObservedAt());
    }

    // ---------- internals ----------

    private void updateNotification(TrackedProduct p, Direction dir, BigDecimal price, BigDecimal before, Instant now) {
        NotificationState n = p.getNotification();
        BigDecimal target = p.getTargetPrice();
        if (target != null) {
            if (price.compareTo(target) <= 0) {
                if (!n.isTargetAlertSent()) {
                    n.setTargetAlertSent(true);
                    queue(n, NotificationType.TARGET_REACHED, price, now);
                }
            } else {
                n.setTargetAlertSent(false); // re-arm once the price climbs back above target
            }
        }
        // A change event produces at most one pending notification; unchanged prices never notify.
        if (dir == Direction.DECREASED) queue(n, NotificationType.DROPPED, price, now);
        else if (dir == Direction.INCREASED) queue(n, NotificationType.INCREASED, price, now);
    }

    private void queue(NotificationState n, NotificationType type, BigDecimal price, Instant now) {
        NotificationType existing = n.getPendingType();
        if (existing == null || type.ordinal() >= existing.ordinal()) {
            n.setPendingType(type);
            n.setPendingPrice(price);
            n.setPendingAt(now);
        }
    }

    private <T> T withLock(String id, java.util.function.Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(id, k -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private static ApiException duplicate(String existingId) {
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_TRACKING", "You are already tracking this product.")
                .withProductId(existingId);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
