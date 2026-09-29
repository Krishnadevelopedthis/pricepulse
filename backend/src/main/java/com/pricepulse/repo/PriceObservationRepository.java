package com.pricepulse.repo;

import com.pricepulse.model.PriceObservation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PriceObservationRepository extends MongoRepository<PriceObservation, String> {
    /** Newest first; callers reverse for charting. */
    List<PriceObservation> findByProductIdOrderByObservedAtDesc(String productId, Pageable page);
    void deleteByProductId(String productId);
}
