# Entra ID Login (SSO + MFA) for the Java Servlet App — Contractor Checklist

> **Goal:** users sign in to the application with their company Microsoft account (Entra ID).
> Microsoft handles password, **MFA** and **SSO**; the app only receives a verified identity.
> **Reference code:** the `demo-auth-entraID` project in this folder (see `README.md`). It is small and working; copy from it.

---

## 0. Read first (5 minutes)

| Concept | What it means for you |
|---|---|
| **OpenID Connect (OIDC)** | Standard login protocol on top of OAuth 2.0. We use the **Authorization Code flow + PKCE**. |
| **The app never sees the password** | The browser goes to `login.microsoftonline.com`, the user signs in there, and Microsoft sends back a one-time **code**. The server exchanges the code for an **ID token** (a signed JWT that says who the user is). |
| **MFA** | **Not coded in the app.** It is a policy in Entra ID (Conditional Access). Microsoft asks for MFA on its own sign-in page. |
| **SSO** | **Not coded in the app.** If the browser already has a Microsoft session (Outlook/Teams/Windows sign-in), the user is signed in without typing anything again. |
| **MSAL4J** | Microsoft's official Java library (`com.microsoft.azure:msal4j`). It builds the login URL and exchanges the code. |
| **oid** | Object ID in the token: permanent unique user ID. **Use it as the link to your user table.** Do not use e-mail/UPN as the key; those can change. |

```
Browser ──(1) GET /protected──▶ App ──(2) 302 to Microsoft login──▶ Entra ID
                                                                   │ password + MFA (or SSO: nothing)
Browser ◀──────────────────(3) form POST code+state to /auth/redirect◀┘
App ──(4) code + secret + PKCE verifier──▶ Entra token endpoint ──▶ ID token
App: validate token → create session → user is signed in
```

---

## 1. Entra ID setup (done by BIENDONG IT, not the contractor)

- [ ] **1.1 Register the app**: Entra admin center → *App registrations* → *New registration*
  - Supported account types: **Single tenant** (only our organization)
  - Redirect URI: platform **Web**, e.g. `https://<app-host>/auth/redirect` (DEV: `http://localhost:8080/auth/redirect`)
- [ ] **1.2 Add a second Web redirect URI** for after sign-out, e.g. `https://<app-host>/`. Entra only accepts a `post_logout_redirect_uri` that is registered.
- [ ] **1.3 Create a client secret**: *Certificates & secrets* → *New client secret*. Copy the **Value** (not the *Secret ID*). Write down the expiry date.
- [ ] **1.4 Restrict who can sign in**: *Enterprise applications* → the app → *Properties* → **Assignment required = Yes**, then *Users and groups* → add the allowed users/groups.
- [ ] **1.5 Enforce MFA**: *Conditional Access* → New policy → Target resources = this app → Grant = **Require multifactor authentication** (needs Entra ID P1). Without P1, *Security Defaults* is the fallback: it applies MFA tenant-wide, but Microsoft decides when to prompt, so you cannot force MFA on every sign-in to this app.
- [ ] **1.6 Send the contractor** (secure channel): **Tenant ID**, **Client ID**, **Client secret** (DEV only; PROD secret stays with the ops team), and the registered redirect URIs.

## 2. Build the login (contractor)

Demo file to copy from is shown in brackets.

- [ ] **2.1 Add dependency** `com.microsoft.azure:msal4j` (latest 1.x) (`pom.xml`)
- [ ] **2.2 Externalize config**: `tenant.id`, `client.id`, `client.secret`, `redirect.uri`, `post.logout.redirect.uri`. The secret comes from an env var or vault and is **never committed to git** (`AuthConfig.java`, `auth.properties.example`)
- [ ] **2.3 Create one `ConfidentialClientApplication`** at startup with authority `https://login.microsoftonline.com/<tenant-id>/` and reuse it (it is thread-safe) (`EntraAuthService`, `AppStartup`)
- [ ] **2.4 `/auth/login`**: generate random **state**, **nonce** and **PKCE code_verifier**, save them in the HTTP session, then redirect to `getAuthorizationRequestUrl(...)` (`LoginServlet`)
- [ ] **2.5 `/auth/redirect`** (handle **POST**, since MSAL4J uses `response_mode=form_post`) (`RedirectServlet`):
  - [ ] if `error` param is present → show a friendly error page
  - [ ] check `state` = session value (CSRF protection), one-time use
  - [ ] `acquireToken(AuthorizationCodeParameters...codeVerifier(...))`
  - [ ] validate ID token claims: `nonce`, `aud` = client ID, `tid` = our tenant, `iss` = `https://login.microsoftonline.com/<tenant-id>/v2.0`, `exp`
  - [ ] `request.changeSessionId()` (prevents session fixation), then store the user in the session
- [ ] **2.6 Protect pages with a Filter**: no user in the session → redirect to `/auth/login?returnTo=...` and only accept **relative** `returnTo` paths (`AuthFilter`)
- [ ] **2.7 `/auth/logout`**: `session.invalidate()`, then redirect to `https://login.microsoftonline.com/<tenant-id>/oauth2/v2.0/logout?post_logout_redirect_uri=...` (`LogoutServlet`)
- [ ] **2.8 Session cookie `SameSite=None; Secure; HttpOnly`**. Without it the browser drops the cookie on Microsoft's cross-site POST and login fails. Servlet 6: `web.xml` `<cookie-config>`. Tomcat 9: `context.xml` `<CookieProcessor sameSiteCookies="none"/>` (`web.xml`)

## 3. Connect to existing users (current AD / local login)

- [ ] **3.1 Map Entra users to the app's user table**: add column `entra_oid`. On first Entra login, match on `preferred_username` (UPN) or e-mail, then save `oid`. On later logins, look up by `oid` only.
- [ ] **3.2 Load app permissions from the app's own DB** by that user record, the same as today. No Entra group/role mapping is needed for phase 1.
- [ ] **3.3 Decide what happens to the old logins**:
  - AD/LDAP login → **remove** (Entra replaces it; MFA cannot be bypassed)
  - Local username/password → keep only for accounts that have no Entra identity (e.g. external or break-glass accounts), or remove. **Agree this with BIENDONG IT.**
- [ ] **3.4 Unknown user** (valid Entra login but not in the app DB) → show an "access not granted, contact IT" page. Do not auto-create an admin.

## 4. Production hardening

- [ ] **4.1 HTTPS only.** The production redirect URI must be `https://`. Behind a reverse proxy / load balancer, configure `redirect.uri` explicitly with the public URL.
- [ ] **4.2 Multiple app servers**: use sticky sessions or session replication (state/nonce live in the session).
- [ ] **4.3 Secret expiry**: note the date; rotate before it expires (the app will stop logging users in). Better: use a **certificate** credential (`ClientCredentialFactory.createFromCertificate`).
- [ ] **4.4 Logging**: log sign-in success/failure with `oid` and the `AADSTS` code. **Never log tokens, codes or the secret.**
- [ ] **4.5 Session timeout** (e.g. 30 min idle). When it expires, the user is sent to Microsoft again and SSO usually signs them in silently.
- [ ] **4.6 Keep MSAL4J up to date** (security fixes).

## 5. Acceptance tests (sign off together)

| # | Test | Expected |
|---|---|---|
| T1 | Open a protected page, not signed in | Redirected to Microsoft sign-in |
| T2 | Sign in with an assigned user | MFA prompt (per policy), then back on the requested page |
| T3 | Already signed in to Office 365 in the same browser, open the app | **No password prompt (SSO)** |
| T4 | User **not** assigned to the app | Microsoft blocks with `AADSTS50105` |
| T5 | Account from another tenant / personal Microsoft account | Rejected |
| T6 | Sign out | App session cleared, Microsoft session ended, protected page asks to sign in again |
| T7 | Sign-in logs in Entra admin center | Entry shows the app name, MFA satisfied, Conditional Access = Success |
| T8 | Replay an old `/auth/redirect` POST / tamper with `state` | Rejected |
| T9 | Entra user with no record in the app DB | "Access not granted" page |

## 6. Common errors → fix

| Symptom | Cause / fix |
|---|---|
| `AADSTS50011` redirect URI mismatch | `redirect.uri` must match the registered URI exactly (http/https, host, port, path, trailing `/`) |
| `AADSTS7000215` invalid client secret | You used the *Secret ID* instead of the *Value*, or the secret expired |
| `AADSTS700016` application not found | Wrong Client ID, or the tenant ID is from another tenant |
| `AADSTS50105` user not assigned | Assignment required = Yes: add the user/group to the Enterprise app |
| App says "No login in progress" / state mismatch | Session cookie not sent on the POST: check `SameSite=None; Secure` and HTTPS; do not mix `localhost` and `127.0.0.1` |
| Works on DEV, fails in PROD behind a proxy | Redirect URI built from the internal host/port; set the public `https://` URL in config |

Look up any `AADSTSxxxxx` code at <https://login.microsoftonline.com/error>.

## References

- Microsoft sample, Java Servlet sign-in with MSAL4J: <https://learn.microsoft.com/en-us/samples/azure-samples/ms-identity-msal-java-samples/java-servlet-webapp-authentication>
- MSAL4J library: <https://github.com/AzureAD/microsoft-authentication-library-for-java>
- ID token claims reference: <https://learn.microsoft.com/en-us/entra/identity-platform/id-token-claims-reference>
- OpenID Connect on Microsoft identity platform: <https://learn.microsoft.com/en-us/entra/identity-platform/v2-protocols-oidc>
