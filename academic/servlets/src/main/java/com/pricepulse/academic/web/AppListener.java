package com.pricepulse.academic.web;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

import java.util.concurrent.atomic.AtomicInteger;

/** Listeners: application start/stop (ServletContextListener) and session create/destroy (HttpSessionListener). */
@WebListener
public class AppListener implements ServletContextListener, HttpSessionListener {
    private static final AtomicInteger ACTIVE_SESSIONS = new AtomicInteger();

    @Override public void contextInitialized(ServletContextEvent e) { e.getServletContext().log("PricePulse academic app started"); }
    @Override public void contextDestroyed(ServletContextEvent e) { e.getServletContext().log("PricePulse academic app stopped"); }
    @Override public void sessionCreated(HttpSessionEvent e) {
        e.getSession().getServletContext().setAttribute("activeSessions", ACTIVE_SESSIONS.incrementAndGet());
    }
    @Override public void sessionDestroyed(HttpSessionEvent e) {
        e.getSession().getServletContext().setAttribute("activeSessions", ACTIVE_SESSIONS.decrementAndGet());
    }
}
