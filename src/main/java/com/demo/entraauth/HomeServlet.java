package com.demo.entraauth;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/** Public home page ("/"). */
@WebServlet("")
public class HomeServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        HttpSession session = req.getSession(false);
        SignedInUser user = session == null ? null : (SignedInUser) session.getAttribute(SignedInUser.SESSION_KEY);
        String ctx = req.getContextPath();

        StringBuilder b = new StringBuilder("<h1>Entra ID sign-in demo</h1>");
        if (user == null) {
            b.append("<p>You are <b>not signed in</b>.</p>")
             .append("<p><a class='btn' href='").append(ctx).append("/auth/login'>Sign in with Microsoft</a></p>")
             .append("<p>Or open the protected page directly: <a href='").append(ctx)
             .append("/app/profile'>/app/profile</a> (you will be sent to Microsoft first).</p>");
        } else {
            b.append("<p class='ok'>Signed in as <b>").append(Html.esc(user.displayName)).append("</b> (")
             .append(Html.esc(user.username)).append(")</p>")
             .append("<p><a class='btn' href='").append(ctx).append("/app/profile'>View my token claims</a> ")
             .append("<a class='btn grey' href='").append(ctx).append("/auth/logout'>Sign out</a></p>");
        }
        b.append("<hr><p style='font-size:13px;color:#666'>Test ideas: sign in &rarr; MFA prompt appears (if policy requires it). "
                + "Then open this site in a new tab or another app using the same tenant: no password again = SSO.</p>");
        Html.page(resp, "Entra ID demo", b.toString());
    }
}
