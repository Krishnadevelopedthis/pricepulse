package com.pricepulse.academic.web;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;

/** Servlet Filter: request timing/logging. Logs method, path and duration only: never parameters, headers or cookies. */
@WebFilter("/*")
public class RequestLogFilter implements Filter {
    private FilterConfig config;

    @Override public void init(FilterConfig config) { this.config = config; }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        long start = System.nanoTime();
        try {
            chain.doFilter(req, res);
        } finally {
            HttpServletRequest r = (HttpServletRequest) req;
            config.getServletContext().log(r.getMethod() + " " + r.getRequestURI() + " took " + (System.nanoTime() - start) / 1_000_000 + " ms");
        }
    }
}
