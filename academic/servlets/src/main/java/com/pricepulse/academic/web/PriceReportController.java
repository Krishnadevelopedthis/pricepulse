package com.pricepulse.academic.web;

import com.pricepulse.academic.jdbc.Db;
import com.pricepulse.academic.jdbc.JdbcPriceHistoryDAO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.regex.Pattern;

/** MVC: JSP (view) <- Servlet (controller) -> Service -> DAO -> Database. Feature: Price History Report. */
@WebServlet("/report")
public class PriceReportController extends HttpServlet {
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        String productId = req.getParameter("productId");
        if (productId == null) { req.getRequestDispatcher("/WEB-INF/views/form.jsp").forward(req, res); return; }
        if (!ID.matcher(productId).matches()) {
            req.setAttribute("error", "Product id must be 1-40 letters, digits, dashes or underscores.");
            req.getRequestDispatcher("/WEB-INF/views/form.jsp").forward(req, res);
            return;
        }
        try (Connection conn = Db.open()) {
            req.setAttribute("report", new PriceReportService(new JdbcPriceHistoryDAO(conn)).build(productId));
            req.getRequestDispatcher("/WEB-INF/views/report.jsp").forward(req, res);
        } catch (SQLException | IllegalStateException e) {
            getServletContext().log("report failed", e);
            throw new ServletException("The report could not be loaded.");  // shown by error.jsp without details
        }
    }
}
