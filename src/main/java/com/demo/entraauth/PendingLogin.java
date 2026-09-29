package com.demo.entraauth;

import java.io.Serializable;

/** Values remembered in the HTTP session between "go to Microsoft" and "Microsoft calls us back". */
public final class PendingLogin implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String SESSION_KEY = "entra.pendingLogin";

    final String state;          // anti-CSRF: must come back unchanged
    final String nonce;          // anti-replay: must appear inside the ID token
    final String codeVerifier;   // PKCE secret: proves we started this login
    final String returnTo;       // page the user wanted before login
    final long createdAtEpochSec;

    PendingLogin(String state, String nonce, String codeVerifier, String returnTo, long createdAtEpochSec) {
        this.state = state;
        this.nonce = nonce;
        this.codeVerifier = codeVerifier;
        this.returnTo = returnTo;
        this.createdAtEpochSec = createdAtEpochSec;
    }

    public String returnTo() {
        return returnTo;
    }
}
