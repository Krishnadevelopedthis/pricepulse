package com.pricepulse.web;

import com.pricepulse.util.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Abuse protection for a public deployment: limits requests per client id and per IP address.
 * The IP is trusted only because server.forward-headers-strategy=native lets the platform proxy set it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {
    private final boolean enabled;
    private final RateLimiter perClient;
    private final RateLimiter perIp;

    public RateLimitFilter(@Value("${pricepulse.rate-limit.enabled:true}") boolean enabled,
                           @Value("${pricepulse.rate-limit.per-client-per-minute:120}") int perClientLimit,
                           @Value("${pricepulse.rate-limit.per-ip-per-minute:300}") int perIpLimit) {
        this.enabled = enabled;
        this.perClient = new RateLimiter(perClientLimit, 60_000);
        this.perIp = new RateLimiter(perIpLimit, 60_000);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !enabled || !req.getRequestURI().startsWith("/api/") || "OPTIONS".equalsIgnoreCase(req.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String ip = "ip:" + req.getRemoteAddr();
        String clientId = req.getHeader("X-Client-Id");
        String clientKey = clientId == null ? null : "c:" + (clientId.length() > 64 ? clientId.substring(0, 64) : clientId);

        boolean ok = perIp.tryAcquire(ip) && (clientKey == null || perClient.tryAcquire(clientKey));
        if (!ok) {
            long retry = Math.max(perIp.retryAfterSeconds(ip), clientKey == null ? 1 : perClient.retryAfterSeconds(clientKey));
            res.setStatus(429);
            res.setHeader("Retry-After", String.valueOf(retry));
            res.setContentType("application/json");
            res.setCharacterEncoding(StandardCharsets.UTF_8.name());
            res.getWriter().write("{\"status\":429,\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests. Try again shortly.\"}");
            return;
        }
        chain.doFilter(req, res);
    }
}
