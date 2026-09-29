package com.pricepulse.academic.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;

/** RequestDispatcher: /dispatch?mode=forward hands the request off; mode=include merges another resource's output. */
@WebServlet("/dispatch")
public class DispatchServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        req.setAttribute("note", "set by DispatchServlet");
        if ("include".equals(req.getParameter("mode"))) {
            res.setContentType("text/plain;charset=UTF-8");
            res.getWriter().println("--- output of DispatchServlet before include ---");
            RequestDispatcher rd = req.getRequestDispatcher("/lifecycle");
            rd.include(req, res);                                  // control returns here afterwards
            res.getWriter().println("--- back in DispatchServlet after include ---");
        } else {
            req.getRequestDispatcher("/lifecycle").forward(req, res); // control does not return; client sees /dispatch URL
        }
    }
}
