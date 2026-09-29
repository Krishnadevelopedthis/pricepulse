package com.pricepulse.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.regex.Pattern;

/**
 * Identifies an extension install by an opaque random id sent as X-Client-Id.
 * This scopes data per install; it is NOT user authentication (see README, "Security").
 */
public class ClientIdInterceptor implements HandlerInterceptor {
    public static final String ATTR = "clientId";
    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9-]{16,64}$");

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) return true;
        String id = req.getHeader("X-Client-Id");
        if (id == null || !VALID.matcher(id).matches()) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "CLIENT_ID_REQUIRED",
                    "A valid X-Client-Id header is required.");
        }
        req.setAttribute(ATTR, id);
        return true;
    }
}
