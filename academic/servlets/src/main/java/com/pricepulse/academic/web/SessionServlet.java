package com.pricepulse.academic.web;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;

/** Session tracking with a Cookie and with HttpSession. /session?action=create|read|invalidate */
@WebServlet("/session")
public class SessionServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setContentType("text/plain;charset=UTF-8");
        String action = req.getParameter("action") == null ? "read" : req.getParameter("action");
        var out = res.getWriter();
        switch (action) {
            case "create" -> {
                Cookie theme = new Cookie("pp_theme", "dark");     // Cookie: small value stored by the browser
                theme.setMaxAge(3600); theme.setHttpOnly(true); theme.setPath(req.getContextPath().isEmpty() ? "/" : req.getContextPath());
                res.addCookie(theme);
                HttpSession session = req.getSession(true);       // HttpSession: data kept on the server, id in JSESSIONID
                session.setAttribute("watchlistCount", 3);
                session.setMaxInactiveInterval(900);
                out.println("created session " + session.isNew() + ", cookie pp_theme set");
            }
            case "invalidate" -> {
                HttpSession s = req.getSession(false);
                if (s != null) s.invalidate();
                out.println("session invalidated");
            }
            default -> {
                HttpSession s = req.getSession(false);            // false: do not create one just to read
                out.println(s == null ? "no active session" : "watchlistCount=" + s.getAttribute("watchlistCount"));
                if (req.getCookies() != null) for (Cookie c : req.getCookies()) if (c.getName().equals("pp_theme")) out.println("cookie pp_theme=" + c.getValue());
            }
        }
    }
}
