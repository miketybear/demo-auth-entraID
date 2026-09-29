package com.demo.entraauth;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/** "/app/profile": protected page that shows who signed in and every claim in the ID token. */
@WebServlet("/app/profile")
public class ProfileServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        SignedInUser user = (SignedInUser) req.getSession().getAttribute(SignedInUser.SESSION_KEY);
        String ctx = req.getContextPath();

        StringBuilder b = new StringBuilder();
        b.append("<h1>Signed in &#10004;</h1>")
         .append("<div class='ok'>Hello <b>").append(Html.esc(user.displayName)).append("</b><br>")
         .append("Username: <code>").append(Html.esc(user.username)).append("</code><br>")
         .append("Object ID (oid): <code>").append(Html.esc(user.oid)).append("</code><br>")
         .append("Tenant ID (tid): <code>").append(Html.esc(user.tenantId)).append("</code><br>")
         .append("Signed in at: ").append(Instant.ofEpochSecond(user.signedInAtEpochSec)).append(" (UTC)</div>")
         .append("<p><a class='btn' href='").append(ctx).append("/'>Home</a> ")
         .append("<a class='btn grey' href='").append(ctx).append("/auth/logout'>Sign out</a></p>")
         .append("<h2>ID token claims</h2><table><tr><th>Claim</th><th>Value</th></tr>");
        for (Map.Entry<String, String> e : user.claims.entrySet()) {
            b.append("<tr><td>").append(Html.esc(e.getKey())).append("</td><td>")
             .append(Html.esc(e.getValue())).append("</td></tr>");
        }
        b.append("</table>")
         .append("<p style='font-size:13px;color:#666'>How to confirm MFA was used: Entra admin center &rarr; "
                 + "Sign-in logs &rarr; open this sign-in &rarr; <i>Authentication Details</i> / "
                 + "<i>Conditional Access</i> tabs.</p>");
        Html.page(resp, "Profile", b.toString());
    }
}
