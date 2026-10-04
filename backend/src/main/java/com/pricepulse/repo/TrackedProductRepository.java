package com.pricepulse.repo;

import com.pricepulse.model.TrackedProduct;
import com.pricepulse.model.TrackingStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TrackedProductRepository extends MongoRepository<TrackedProduct, String> {
    List<TrackedProduct> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<TrackedProduct> findByIdAndUserId(String id, String userId);
    Optional<TrackedProduct> findByUserIdAndNormalizedUrl(String userId, String normalizedUrl);
    long countByUserId(String userId);
    List<TrackedProduct> findByStatusAndNextCheckAtLessThanEqual(TrackingStatus status, Instant now, Pageable page);
    List<TrackedProduct> findByUserIdAndNotificationPendingTypeNotNull(String userId);
    List<TrackedProduct> findByNormalizedUrlContaining(String fragment);
}
