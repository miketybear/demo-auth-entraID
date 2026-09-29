package com.demo.entraauth;

import com.microsoft.aad.msal4j.AuthorizationCodeParameters;
import com.microsoft.aad.msal4j.AuthorizationRequestUrlParameters;
import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;

import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * All Entra ID (OpenID Connect) logic in one place.
 *
 * Flow = OAuth 2.0 Authorization Code + PKCE, confidential client (server keeps a client secret):
 *   1. buildLoginRequest()  -> URL of the Microsoft sign-in page (user does password + MFA there)
 *   2. Microsoft POSTs back to redirect.uri with ?code=...&state=...
 *   3. completeLogin()      -> exchanges the code for an ID token and validates it
 *
 * MFA and SSO are NOT coded here: Entra ID enforces MFA (Conditional Access / Security Defaults)
 * on its own sign-in page, and SSO happens because the browser already has an Entra session cookie.
 */
public final class EntraAuthService {

    /** OIDC scopes only (we just need to know who the user is). MSAL adds openid/profile/offline_access itself. */
    private static final Set<String> SCOPES = Set.of("openid", "profile", "email");

    /** A login attempt must complete within 10 minutes. */
    private static final long LOGIN_TTL_SECONDS = 600;

    /** Allowed clock difference between this server and Entra ID when checking exp. */
    private static final long CLOCK_SKEW_SECONDS = 300;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthConfig config;
    private final ConfidentialClientApplication msal;

    public EntraAuthService(AuthConfig config) {
        this.config = config;
        try {
            // Thread-safe; create ONCE per application and reuse.
            this.msal = ConfidentialClientApplication
                    .builder(config.clientId, ClientCredentialFactory.createFromSecret(config.clientSecret))
                    .authority(config.authority)
                    .build();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Invalid authority URL: " + config.authority, e);
        }
    }

    public AuthConfig config() {
        return config;
    }

    /** Step 1: create state / nonce / PKCE values and the Microsoft sign-in URL. */
    public LoginRequest buildLoginRequest(String returnTo) {
        String state = randomToken();
        String nonce = randomToken();
        String codeVerifier = randomToken() + randomToken(); // 86 chars, within PKCE's 43..128
        String codeChallenge = sha256Base64Url(codeVerifier);

        AuthorizationRequestUrlParameters params = AuthorizationRequestUrlParameters
                .builder(config.redirectUri, SCOPES)
                // response_mode defaults to form_post in MSAL4J: Entra POSTs code+state to redirect.uri
                .state(state)
                .nonce(nonce)
                .codeChallenge(codeChallenge)
                .codeChallengeMethod("S256")
                .build();

        String url = msal.getAuthorizationRequestUrl(params).toString();
        PendingLogin pending = new PendingLogin(state, nonce, codeVerifier, returnTo, Instant.now().getEpochSecond());
        return new LoginRequest(url, pending);
    }

    /** Step 3: check state, exchange the code, validate the ID token, return the signed-in user. */
    public SignedInUser completeLogin(PendingLogin pending, String state, String code) throws AuthException {
        if (pending == null) {
            throw new AuthException("No login in progress for this browser session "
                    + "(session cookie missing? see README: SameSite / HTTPS).");
        }
        if (state == null || !constantTimeEquals(pending.state, state)) {
            throw new AuthException("State mismatch - possible CSRF, or login started in another tab.");
        }
        if (Instant.now().getEpochSecond() - pending.createdAtEpochSec > LOGIN_TTL_SECONDS) {
            throw new AuthException("Login attempt expired. Please sign in again.");
        }
        if (code == null || code.isBlank()) {
            throw new AuthException("Authorization code missing in the response from Entra ID.");
        }

        IAuthenticationResult result;
        try {
            AuthorizationCodeParameters params = AuthorizationCodeParameters
                    .builder(code, URI.create(config.redirectUri))
                    .scopes(SCOPES)
                    .codeVerifier(pending.codeVerifier)
                    .build();
            result = msal.acquireToken(params).get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new AuthException("Token exchange failed: " + cause.getMessage(), cause);
        } catch (TimeoutException e) {
            throw new AuthException("Token exchange timed out (can the server reach login.microsoftonline.com?)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthException("Interrupted during token exchange", e);
        }

        // The ID token came straight from Entra's token endpoint over TLS (back-channel), so per
        // OpenID Connect Core 3.1.3.7 the TLS server check may replace signature validation.
        // We still validate the claims that matter:
        Map<String, ?> claims = result.tenantProfile().getClaims();
        expectEquals("nonce", pending.nonce, str(claims.get("nonce")));    // replay protection
        expectEquals("aud", config.clientId, str(claims.get("aud")));      // token is for THIS app
        expectEquals("tid", config.tenantId, str(claims.get("tid")));      // user is from OUR tenant
        expectEquals("iss", config.expectedIssuer, str(claims.get("iss")));
        long exp = num(claims.get("exp"));
        if (exp + CLOCK_SKEW_SECONDS < Instant.now().getEpochSecond()) {
            throw new AuthException("ID token expired (check server clock).");
        }

        Map<String, String> flat = new TreeMap<>();
        claims.forEach((k, v) -> flat.put(k, String.valueOf(v)));

        return new SignedInUser(
                str(claims.get("oid")),              // stable, unique user ID -> use this as the key in your DB
                str(claims.get("preferred_username")),
                str(claims.get("name")),
                str(claims.get("tid")),
                Instant.now().getEpochSecond(),
                flat);
    }

    /** Microsoft sign-out URL: ends the Entra session, then returns to our home page. */
    public String logoutUrl() {
        return config.authority + "oauth2/v2.0/logout?post_logout_redirect_uri="
                + java.net.URLEncoder.encode(config.postLogoutRedirectUri, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ helpers

    private static void expectEquals(String claim, String expected, String actual) throws AuthException {
        if (actual == null || !expected.equalsIgnoreCase(actual)) {
            throw new AuthException("ID token claim '" + claim + "' is invalid (expected " + expected + ", got " + actual + ")");
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static long num(Object o) throws AuthException {
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            throw new AuthException("ID token has no valid 'exp' claim");
        }
    }

    private static String randomToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String sha256Base64Url(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** Result of step 1: where to send the browser + what to remember in the session. */
    public static final class LoginRequest {
        public final String authorizationUrl;
        public final PendingLogin pending;

        LoginRequest(String authorizationUrl, PendingLogin pending) {
            this.authorizationUrl = authorizationUrl;
            this.pending = pending;
        }
    }
}
