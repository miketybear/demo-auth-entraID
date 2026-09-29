package com.demo.entraauth;

/** Any failure in the sign-in flow. The message is shown on the error page. */
public class AuthException extends Exception {
    private static final long serialVersionUID = 1L;

    public AuthException(String message) {
        super(message);
    }

    public AuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
