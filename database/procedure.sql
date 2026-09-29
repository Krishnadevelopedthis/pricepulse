-- Function used by CallableStatement in JdbcDemo. Returns a refcursor (PostgreSQL's way to return a result set
-- through the JDBC call escape syntax  { ? = call get_product_price_history(?) }).
CREATE OR REPLACE FUNCTION get_product_price_history(p_product_id VARCHAR)
RETURNS refcursor
LANGUAGE plpgsql AS $$
DECLARE
    c refcursor := 'pp_history_cursor';
BEGIN
    OPEN c FOR
        SELECT id, price, currency, observed_at, source
        FROM price_history
        WHERE product_id = p_product_id
        ORDER BY observed_at;
    RETURN c;
END;
$$;
