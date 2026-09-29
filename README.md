# demo-auth-entraID: Entra ID sign-in (SSO + MFA) for a Java Servlet app

Minimal working demo that signs users in with the company's Entra ID tenant using
**MSAL4J** (OpenID Connect, Authorization Code + PKCE). No Spring, no JSP: plain Servlets + one Filter,
so the code can be copied into an existing Servlet application.

The contractor checklist is in [`todos.md`](todos.md).

## Requirements

- JDK **17+** and Maven **3.9+**
- Network access from the machine to `login.microsoftonline.com` and to Maven Central

## 1. Register the app in Entra ID (about 5 minutes)

1. <https://entra.microsoft.com> → *Identity* → *Applications* → **App registrations** → **New registration**
   - Name: `demo-auth-entraID (DEV)`
   - Supported account types: **Accounts in this organizational directory only (Single tenant)**
   - Redirect URI: platform **Web**, `http://localhost:8080/auth/redirect`
2. On the new app → **Authentication** → *Web* → **Add URI** `http://localhost:8080/` (used after sign-out) → Save
3. **Certificates & secrets** → **New client secret** → copy the **Value** right away (it is only shown once)
4. **Overview** → copy **Application (client) ID** and **Directory (tenant) ID**
5. (Optional, to test MFA/assignment) *Enterprise applications* → this app → *Properties* → **Assignment required = Yes**,
   add your test users; create a **Conditional Access** policy targeting this app with *Require multifactor authentication*.

No API permissions need to be added (the default `User.Read` is enough; the demo only uses `openid profile email`).

## 2. Configure

```bash
copy auth.properties.example auth.properties      # Windows   (cp on Linux/macOS)
```

Fill in `tenant.id`, `client.id`, `client.secret` in `auth.properties`.
Alternatively set the secret as an environment variable instead of in the file:

```bash
set AUTH_CLIENT_SECRET=xxxxx          # Windows cmd
$env:AUTH_CLIENT_SECRET="xxxxx"       # PowerShell
```

## 3. Run

```bash
mvn jetty:run
```

Open **http://localhost:8080** (use `localhost`, not `127.0.0.1`: it must match the redirect URI).

| URL | What it does |
|---|---|
| `/` | Public home page with a *Sign in with Microsoft* button |
| `/auth/login` | Starts the login (redirect to Microsoft) |
| `/auth/redirect` | Redirect URI: Microsoft POSTs the code here |
| `/app/profile` | Protected page: shows the user and all ID-token claims |
| `/auth/logout` | Signs out of the app and Microsoft |

To deploy on Tomcat 10.1+ instead: `mvn package`, then copy `target/demo-auth-entraid.war` to `webapps/ROOT.war`
(or keep the name and change both redirect URIs to `http://localhost:8080/demo-auth-entraid/...`).

## 4. What to check

- [ ] Sign in → you land on `/app/profile` with your name, `oid`, `tid`
- [ ] **MFA**: with the Conditional Access policy on, Microsoft asks for MFA. Confirm in Entra admin center →
      *Sign-in logs* → the entry for this app → *Authentication Details* and *Conditional Access* tabs
- [ ] **SSO**: after signing in, stop and restart `mvn jetty:run` (this clears the app session), then open
      `http://localhost:8080/app/profile` in the same browser. You get back in with no password or MFA prompt, because the
      Microsoft session is still active. The same happens in a browser already signed in to Outlook/Teams.
- [ ] **Sign out** → Microsoft sign-out page → back to home; `/app/profile` asks you to sign in again
- [ ] A user not assigned to the app (if assignment is required) gets `AADSTS50105`

## Code map

| File | Role |
|---|---|
| `AuthConfig.java` | Reads `auth.properties` (or `-Dauth.config=...` / `AUTH_CONFIG`), secret can come from `AUTH_CLIENT_SECRET` |
| `EntraAuthService.java` | **All auth logic**: login URL (state, nonce, PKCE), code exchange, ID-token claim validation, logout URL |
| `AppStartup.java` | Creates one `EntraAuthService` at startup |
| `LoginServlet.java` | `/auth/login` |
| `RedirectServlet.java` | `/auth/redirect`: error handling, state check, token exchange, `changeSessionId()` |
| `AuthFilter.java` | Protects `/app/*` |
| `LogoutServlet.java` | `/auth/logout` |
| `ProfileServlet.java`, `HomeServlet.java` | Demo pages |
| `web.xml` | Session cookie `HttpOnly; Secure; SameSite=None` (required for the form-POST callback) |

## Using `javax.servlet` (Tomcat 9 / Servlet 4)

1. Replace `jakarta.servlet` with `javax.servlet` in all imports.
2. In `pom.xml` use `javax.servlet:javax.servlet-api:4.0.1` (scope `provided`) and remove the Jetty ee10 plugin.
3. `web.xml`: use the Servlet 4 schema and remove the `<attribute>` SameSite element; instead set it in
   `META-INF/context.xml`: `<Context><CookieProcessor sameSiteCookies="none"/></Context>` (Tomcat 9.0.21+).
4. The code uses Java 11 APIs (`String.isBlank`, `Set.of`), so the runtime must be Java 11+.

## Notes on security choices

- **Single tenant** authority `https://login.microsoftonline.com/<tenant-id>/`, plus `tid`/`iss` checks: only our tenant.
- The ID token is received directly from Entra's token endpoint over TLS (back channel). Per OpenID Connect Core §3.1.3.7
  this lets the TLS check replace JWT signature verification; the demo still checks `nonce`, `aud`, `tid`, `iss`, `exp`.
- Tokens are **not** stored in the session; only the user's identity is. Add token caching only if the app later calls
  Microsoft Graph or another API.
