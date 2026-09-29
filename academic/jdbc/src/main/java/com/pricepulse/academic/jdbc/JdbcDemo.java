package com.pricepulse.academic.jdbc;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.List;

/**
 * Runnable tour of JDBC (needs PostgreSQL; see database/README.md).
 * Run:  mvn -q compile exec:java   (with DB_URL, DB_USER, DB_PASSWORD set)
 */
public class JdbcDemo {
    public static void main(String[] args) throws Exception {
        try (Connection conn = Db.open()) {
            statementCrud(conn);
            preparedStatementAndInjection(conn);
            PriceHistoryDAO dao = new JdbcPriceHistoryDAO(conn);
            daoDemo(dao);
            callableDemo(conn);
            metadataDemo(conn);
            transactionDemo(conn, dao);
            batchDemo(conn);
        }
    }

    /** Statement: fine for fixed SQL with no user-controlled values. */
    static void statementCrud(Connection conn) throws SQLException {
        System.out.println("== Statement CRUD (constant SQL only) ==");
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("DELETE FROM products WHERE id = 'demo-1'");
            st.executeUpdate("INSERT INTO products (id, name, url, currency) VALUES ('demo-1', 'Trail Shoe', 'https://shop.example.com/p/1', 'INR')");
            st.executeUpdate("UPDATE products SET name = 'Trail Shoe v2' WHERE id = 'demo-1'");
            try (ResultSet rs = st.executeQuery("SELECT id, name, currency FROM products WHERE id = 'demo-1'")) {
                while (rs.next()) System.out.println("  " + rs.getString("id") + " | " + rs.getString("name") + " | " + rs.getString(3)); // by name and by index
            }
        }
    }

    /**
     * PreparedStatement: SQL is compiled first, values are bound afterwards as data, never as SQL text.
     * That is why it is the right choice for anything user-controlled: SQL injection cannot change the query.
     */
    static void preparedStatementAndInjection(Connection conn) throws SQLException {
        System.out.println("== PreparedStatement and SQL injection ==");
        String hostile = "x' OR '1'='1";
        try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM products WHERE id = ?")) {
            ps.setString(1, hostile);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); System.out.println("  hostile input matched " + rs.getInt(1) + " rows (bound as plain text)"); }
        }
        // Do NOT do this: "SELECT ... WHERE id = '" + hostile + "'"  would return every row.
    }

    static void daoDemo(PriceHistoryDAO dao) throws SQLException {
        System.out.println("== DAO ==");
        PriceRecord saved = dao.save(new PriceRecord(0, "demo-1", new BigDecimal("3499.00"), "INR", Instant.now(), "SERVER"));
        dao.update(saved.id(), new BigDecimal("3399.00"));
        System.out.println("  saved id=" + saved.id() + ", rows for demo-1: " + dao.findByProduct("demo-1").size());
        dao.delete(saved.id());
    }

    /** CallableStatement: calls the stored function from database/procedure.sql. */
    static void callableDemo(Connection conn) throws SQLException {
        System.out.println("== CallableStatement ==");
        conn.setAutoCommit(false); // refcursors live inside a transaction on PostgreSQL
        try (CallableStatement cs = conn.prepareCall("{ ? = call get_product_price_history(?) }")) {
            cs.registerOutParameter(1, Types.OTHER);
            cs.setString(2, "demo-1");
            cs.execute();
            try (ResultSet rs = (ResultSet) cs.getObject(1)) {
                while (rs.next()) {
                    System.out.printf("  #%d %s %s at %s (%s)%n", rs.getInt("id"), rs.getBigDecimal("price"),
                            rs.getString("currency"), rs.getTimestamp("observed_at"), rs.getString("source"));
                }
            }
        }
        conn.commit();
        conn.setAutoCommit(true);
    }

    static void metadataDemo(Connection conn) throws SQLException {
        System.out.println("== DatabaseMetaData / ResultSetMetaData ==");
        DatabaseMetaData md = conn.getMetaData();
        System.out.println("  product: " + md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());
        System.out.println("  driver : " + md.getDriverName() + " " + md.getDriverVersion());
        try (ResultSet tables = md.getTables(null, "public", "%", new String[]{"TABLE"})) {
            while (tables.next()) System.out.println("  table  : " + tables.getString("TABLE_NAME"));
        }
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT id, price, observed_at FROM price_history LIMIT 1")) {
            ResultSetMetaData rmd = rs.getMetaData();
            for (int i = 1; i <= rmd.getColumnCount(); i++) System.out.println("  column " + i + ": " + rmd.getColumnName(i) + " (" + rmd.getColumnTypeName(i) + ")");
        }
    }

    /** Multi-step transaction: record a new price AND move the product to a new name; both or neither. */
    static void transactionDemo(Connection conn, PriceHistoryDAO dao) throws SQLException {
        System.out.println("== Transaction ==");
        conn.setAutoCommit(false);
        try {
            dao.save(new PriceRecord(0, "demo-1", new BigDecimal("3199.00"), "INR", Instant.now(), "SERVER"));
            try (PreparedStatement ps = conn.prepareStatement("UPDATE products SET name = ? WHERE id = ?")) {
                ps.setString(1, "Trail Shoe (sale)");
                ps.setString(2, "demo-1");
                ps.executeUpdate();
            }
            dao.save(new PriceRecord(0, "demo-1", new BigDecimal("-1"), "INR", Instant.now(), "SERVER")); // violates CHECK (price > 0)
            conn.commit();
        } catch (SQLException e) {
            conn.rollback(); // the first two writes are undone as well
            System.out.println("  rolled back after error: " + e.getMessage().split("\n")[0]);
        } finally {
            conn.setAutoCommit(true);
        }
        System.out.println("  rows after rollback: " + dao.findByProduct("demo-1").size());
    }

    static void batchDemo(Connection conn) throws SQLException {
        System.out.println("== Batch ==");
        String sql = "INSERT INTO price_history (product_id, price, currency, source) VALUES (?, ?, 'INR', 'SERVER')";
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (int i = 0; i < 5; i++) { ps.setString(1, "demo-1"); ps.setBigDecimal(2, new BigDecimal(3000 + i * 25)); ps.addBatch(); }
            int[] counts = ps.executeBatch();
            conn.commit();
            System.out.println("  inserted " + counts.length + " rows in one round trip");
        } finally { conn.setAutoCommit(true); }
    }
}
