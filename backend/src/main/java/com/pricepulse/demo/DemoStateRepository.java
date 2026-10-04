package com.pricepulse.demo;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface DemoStateRepository extends MongoRepository<DemoState, String> {
}
