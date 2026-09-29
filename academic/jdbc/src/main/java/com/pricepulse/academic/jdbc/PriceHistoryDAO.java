package com.pricepulse.academic.jdbc;

import java.sql.SQLException;
import java.util.List;

/** DAO pattern: business code depends on this interface, never on SQL. */
public interface PriceHistoryDAO {
    PriceRecord save(PriceRecord record) throws SQLException;          // returns the row with its generated id
    List<PriceRecord> findByProduct(String productId) throws SQLException;
    boolean update(long id, java.math.BigDecimal newPrice) throws SQLException;
    boolean delete(long id) throws SQLException;
}
