package com.demo.entraauth;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * "/auth/redirect" = the Redirect URI registered in Entra ID.
 * Entra sends code + state here with an HTML form POST (response_mode=form_post).
 */
@WebServlet("/auth/redirect")
public class RedirectServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        handle(req, resp);
    }

    private void handle(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        EntraAuthService auth = AppStartup.service(getServletContext());
        HttpSession session = req.getSession(false);
        PendingLogin pending = session == null ? null : (PendingLogin) session.getAttribute(PendingLogin.SESSION_KEY);
        if (session != null) session.removeAttribute(PendingLogin.SESSION_KEY); // one-time use

        // Entra reports problems (user cancelled, MFA failed, not assigned to app, ...) as error + error_description
        String error = req.getParameter("error");
        if (error != null) {
            showError(req, resp, "Entra ID returned an error: " + error, req.getParameter("error_description"));
            return;
        }

        try {
            SignedInUser user = auth.completeLogin(pending, req.getParameter("state"), req.getParameter("code"));

            // New session ID after login = protection against session fixation.
            req.changeSessionId();
            req.getSession().setAttribute(SignedInUser.SESSION_KEY, user);
            log("Signed in: " + user.username + " oid=" + user.oid);

            String target = pending.returnTo() != null ? pending.returnTo() : "/app/profile";
            resp.sendRedirect(req.getContextPath() + target);
        } catch (AuthException e) {
            log("Sign-in failed: " + e.getMessage(), e.getCause());
            showError(req, resp, "Sign-in failed", e.getMessage());
        }
    }

    private static void showError(HttpServletRequest req, HttpServletResponse resp, String title, String detail)
            throws IOException {
        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        Html.page(resp, "Sign-in error",
                "<h1>Sign-in error</h1><div class='err'><b>" + Html.esc(title) + "</b><br><pre style='white-space:pre-wrap'>"
                        + Html.esc(detail) + "</pre></div>"
                        + "<p><a class='btn' href='" + req.getContextPath() + "/auth/login'>Try again</a> "
                        + "<a class='btn grey' href='" + req.getContextPath() + "/'>Home</a></p>"
                        + "<p style='font-size:13px'>Tip: an <code>AADSTSxxxxx</code> code can be looked up at "
                        + "https://login.microsoftonline.com/error</p>");
    }
}
