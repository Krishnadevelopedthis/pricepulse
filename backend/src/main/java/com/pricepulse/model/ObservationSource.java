package com.pricepulse.model;

/** Where a price observation came from. */
public enum ObservationSource {
    BROWSER_INITIAL,   // detected in the user's browser when tracking started
    BROWSER,           // reported by the extension while the user viewed the product page
    SERVER             // fetched by the backend scheduler / manual server check
}
