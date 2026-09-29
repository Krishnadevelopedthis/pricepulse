package com.pricepulse.academic.jdbc;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** DAO Interface -> JDBC implementation -> relational database. Every value is bound with PreparedStatement. */
public class JdbcPriceHistoryDAO implements PriceHistoryDAO {
    private final Connection conn;

    public JdbcPriceHistoryDAO(Connection conn) {
        this.conn = conn;
    }

    @Override
    public PriceRecord save(PriceRecord r) throws SQLException {
        String sql = "INSERT INTO price_history (product_id, price, currency, observed_at, source) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, r.productId());
            ps.setBigDecimal(2, r.price());
            ps.setString(3, r.currency());
            ps.setTimestamp(4, Timestamp.from(r.observedAt()));
            ps.setString(5, r.source());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new PriceRecord(keys.getLong("id"), r.productId(), r.price(), r.currency(), r.observedAt(), r.source());
            }
        }
    }

    @Override
    public List<PriceRecord> findByProduct(String productId) throws SQLException {
        String sql = "SELECT id, product_id, price, currency, observed_at, source FROM price_history WHERE product_id = ? ORDER BY observed_at";
        List<PriceRecord> out = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new PriceRecord(rs.getLong("id"), rs.getString("product_id"), rs.getBigDecimal("price"),
                            rs.getString("currency"), rs.getTimestamp("observed_at").toInstant(), rs.getString("source")));
                }
            }
        }
        return out;
    }

    @Override
    public boolean update(long id, BigDecimal newPrice) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("UPDATE price_history SET price = ? WHERE id = ?")) {
            ps.setBigDecimal(1, newPrice);
            ps.setLong(2, id);
            return ps.executeUpdate() == 1;
        }
    }

    @Override
    public boolean delete(long id) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM price_history WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() == 1;
        }
    }
}
