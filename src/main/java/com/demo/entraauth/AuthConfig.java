package com.demo.entraauth;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Loads the Entra ID settings.
 *
 * Lookup order for the properties file:
 *   1. JVM system property  -Dauth.config=/path/to/auth.properties
 *   2. environment variable AUTH_CONFIG=/path/to/auth.properties
 *   3. ./auth.properties in the working directory (project root when using `mvn jetty:run`)
 *
 * The client secret can also come from the environment variable AUTH_CLIENT_SECRET,
 * which wins over the file. Never commit the real secret to source control.
 */
public final class AuthConfig {

    private static final Pattern GUID =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    public final String tenantId;
    public final String clientId;
    public final String clientSecret;
    public final String redirectUri;
    public final String postLogoutRedirectUri;

    /** https://login.microsoftonline.com/{tenantId}/ — single-tenant: only users of OUR tenant can sign in. */
    public final String authority;
    /** Expected "iss" claim of the ID token. */
    public final String expectedIssuer;

    private AuthConfig(Properties p) {
        this.tenantId = required(p, "tenant.id");
        this.clientId = required(p, "client.id");
        String envSecret = System.getenv("AUTH_CLIENT_SECRET");
        this.clientSecret = (envSecret != null && !envSecret.isBlank()) ? envSecret.trim() : required(p, "client.secret");
        this.redirectUri = required(p, "redirect.uri");
        this.postLogoutRedirectUri = required(p, "post.logout.redirect.uri");

        if (!GUID.matcher(tenantId).matches()) {
            throw new IllegalStateException("tenant.id must be the Directory (tenant) ID GUID, not a domain name: " + tenantId);
        }
        if (!GUID.matcher(clientId).matches()) {
            throw new IllegalStateException("client.id must be the Application (client) ID GUID: " + clientId);
        }
        this.authority = "https://login.microsoftonline.com/" + tenantId + "/";
        this.expectedIssuer = "https://login.microsoftonline.com/" + tenantId + "/v2.0";
    }

    public static AuthConfig load() {
        String location = System.getProperty("auth.config");
        if (location == null || location.isBlank()) location = System.getenv("AUTH_CONFIG");
        if (location == null || location.isBlank()) location = "auth.properties";

        Path path = Paths.get(location).toAbsolutePath();
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Entra ID config file not found: " + path
                    + " (copy auth.properties.example to auth.properties and fill it in)");
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            p.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + path, e);
        }
        return new AuthConfig(p);
    }

    private static String required(Properties p, String key) {
        String v = p.getProperty(key);
        if (v == null || v.isBlank() || v.contains("<") ) {
            throw new IllegalStateException("Missing value for '" + key + "' in auth.properties");
        }
        return v.trim();
    }
}
