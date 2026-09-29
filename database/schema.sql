-- Academic JDBC module ONLY. The PricePulse application itself stores everything in MongoDB.
-- PostgreSQL. Create a database first:  createdb pricepulse_academic
CREATE TABLE IF NOT EXISTS products (
    id        VARCHAR(40)  PRIMARY KEY,
    name      VARCHAR(300) NOT NULL,
    url       VARCHAR(2048) NOT NULL,
    currency  CHAR(3)      NOT NULL
);

CREATE TABLE IF NOT EXISTS price_history (
    id           BIGSERIAL     PRIMARY KEY,
    product_id   VARCHAR(40)   NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    price        NUMERIC(12,2) NOT NULL CHECK (price > 0),
    currency     CHAR(3)       NOT NULL,
    observed_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    source       VARCHAR(20)   NOT NULL DEFAULT 'SERVER'
);
CREATE INDEX IF NOT EXISTS idx_history_product_time ON price_history (product_id, observed_at);
