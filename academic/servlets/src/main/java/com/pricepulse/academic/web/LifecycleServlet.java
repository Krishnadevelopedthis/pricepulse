package com.pricepulse.academic.web;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebInitParam;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

/** Servlet lifecycle (init -> service -> destroy), init parameters, ServletContext, GET and POST. */
@WebServlet(urlPatterns = "/lifecycle", initParams = @WebInitParam(name = "greeting", value = "PricePulse academic servlet"))
public class LifecycleServlet extends HttpServlet {
    private String greeting;
    private final AtomicInteger requests = new AtomicInteger(); // one servlet instance serves many threads

    @Override
    public void init() throws ServletException {           // called once, before the first request
        greeting = getInitParameter("greeting");           // servlet init-param
        getServletContext().log("LifecycleServlet.init()");
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        requests.incrementAndGet();                        // called for every request, then dispatches to doGet/doPost
        super.service(req, res);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("text/plain;charset=UTF-8");
        ServletContext ctx = getServletContext();
        res.getWriter().printf("%s%nmethod=GET requests so far=%d%napp name (context-param)=%s%ncontext path=%s%nserver=%s%n",
                greeting, requests.get(), ctx.getInitParameter("appName"), ctx.getContextPath(), ctx.getServerInfo());
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String product = req.getParameter("product");
        if (product == null || product.isBlank() || product.length() > 100) { res.sendError(HttpServletResponse.SC_BAD_REQUEST, "product is required (max 100 chars)"); return; }
        res.setContentType("text/plain;charset=UTF-8");
        res.getWriter().println("method=POST received product length=" + product.length()); // never echo raw input into HTML
    }

    @Override
    public void destroy() {                                // called once when the container unloads the servlet
        getServletContext().log("LifecycleServlet.destroy() after " + requests.get() + " requests");
    }
}
