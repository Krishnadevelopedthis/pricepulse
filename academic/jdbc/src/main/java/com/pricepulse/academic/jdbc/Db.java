package com.pricepulse.academic.jdbc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * JDBC architecture: Application -> DriverManager -> JDBC driver (org.postgresql.Driver) -> database.
 * DriverManager picks a registered driver whose URL prefix matches (jdbc:postgresql:...). Credentials come from
 * environment variables and are never hardcoded.
 */
public final class Db {
    private Db() {}

    public static Connection open() throws SQLException {
        String url = require("DB_URL"), user = require("DB_USER"), pass = System.getenv("DB_PASSWORD");
        return DriverManager.getConnection(url, user, pass == null ? "" : pass);
    }

    private static String require(String name) {
        String v = System.getenv(name);
        if (v == null || v.isBlank()) throw new IllegalStateException("Set the " + name + " environment variable.");
        return v;
    }
}
