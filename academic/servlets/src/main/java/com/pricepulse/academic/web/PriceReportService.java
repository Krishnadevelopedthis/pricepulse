package com.pricepulse.academic.web;

import com.pricepulse.academic.jdbc.PriceHistoryDAO;
import com.pricepulse.academic.jdbc.PriceRecord;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;

/** MVC "Service": business rules on top of the DAO (no SQL here, no HTML there). */
public class PriceReportService {
    public record Report(String productId, List<PriceRecord> rows, BigDecimal lowest, BigDecimal highest, BigDecimal latest) {
        public int getCount() { return rows.size(); }
        public String getProductId() { return productId; }
        public List<PriceRecord> getRows() { return rows; }
        public BigDecimal getLowest() { return lowest; }
        public BigDecimal getHighest() { return highest; }
        public BigDecimal getLatest() { return latest; }
    }

    private final PriceHistoryDAO dao;

    public PriceReportService(PriceHistoryDAO dao) { this.dao = dao; }

    public Report build(String productId) throws SQLException {
        List<PriceRecord> rows = dao.findByProduct(productId);
        if (rows.isEmpty()) return new Report(productId, rows, null, null, null);
        BigDecimal low = rows.stream().map(PriceRecord::price).min(Comparator.naturalOrder()).get();
        BigDecimal high = rows.stream().map(PriceRecord::price).max(Comparator.naturalOrder()).get();
        return new Report(productId, rows, low, high, rows.get(rows.size() - 1).price());
    }
}
