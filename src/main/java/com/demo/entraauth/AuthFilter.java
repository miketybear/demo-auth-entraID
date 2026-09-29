package com.demo.entraauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Protects everything under /app/*. Not signed in -> go to /auth/login and come back afterwards.
 * In the real application, map this filter to all protected URLs.
 */
@WebFilter("/app/*")
public class AuthFilter extends HttpFilter {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpSession session = req.getSession(false);
        SignedInUser user = session == null ? null : (SignedInUser) session.getAttribute(SignedInUser.SESSION_KEY);
        if (user != null) {
            chain.doFilter(req, resp);
            return;
        }
        String path = req.getRequestURI().substring(req.getContextPath().length());
        if (req.getQueryString() != null) path += "?" + req.getQueryString();
        resp.sendRedirect(req.getContextPath() + "/auth/login?returnTo="
                + java.net.URLEncoder.encode(path, java.nio.charset.StandardCharsets.UTF_8));
    }
}
