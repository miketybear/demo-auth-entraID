package com.demo.entraauth;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;

/** The authenticated user, stored in the HTTP session under SESSION_KEY. */
public final class SignedInUser implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String SESSION_KEY = "entra.user";

    /** Object ID: immutable, unique per user in the tenant. Use it to link to your own user table. */
    public final String oid;
    /** Usually the UPN / e-mail, e.g. nhatnh@company.com (can change - do not use as a key). */
    public final String username;
    public final String displayName;
    public final String tenantId;
    public final long signedInAtEpochSec;
    /** All ID-token claims (for the demo page only). */
    public final Map<String, String> claims;

    SignedInUser(String oid, String username, String displayName, String tenantId,
                 long signedInAtEpochSec, Map<String, String> claims) {
        this.oid = oid;
        this.username = username;
        this.displayName = displayName;
        this.tenantId = tenantId;
        this.signedInAtEpochSec = signedInAtEpochSec;
        this.claims = Collections.unmodifiableMap(claims);
    }
}
