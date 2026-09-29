package com.demo.entraauth;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** "/auth/login": remember state/nonce/PKCE in the session, then send the browser to Microsoft. */
@WebServlet("/auth/login")
public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        EntraAuthService auth = AppStartup.service(getServletContext());

        EntraAuthService.LoginRequest login = auth.buildLoginRequest(safeReturnTo(req.getParameter("returnTo")));
        req.getSession(true).setAttribute(PendingLogin.SESSION_KEY, login.pending);
        resp.sendRedirect(login.authorizationUrl);
    }

    /** Only allow local paths like "/app/x" - never "//evil.com" or "https://..." (open-redirect protection). */
    static String safeReturnTo(String returnTo) {
        if (returnTo == null || !returnTo.startsWith("/") || returnTo.startsWith("//") || returnTo.contains("\\")) {
            return null;
        }
        return returnTo;
    }
}
