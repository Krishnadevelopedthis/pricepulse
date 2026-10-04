package com.pricepulse.demo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Single document holding the demo product's current price (whole rupees), so restarts keep it. */
@Document("demo_state")
public class DemoState {
    @Id
    private String id;
    private long price;

    public DemoState() {}

    public DemoState(String id, long price) {
        this.id = id;
        this.price = price;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }
}
