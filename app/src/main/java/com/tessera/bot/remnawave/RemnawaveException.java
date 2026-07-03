package com.tessera.bot.remnawave;

/** Raised when the Remnawave API returns a non-success response or a transport error occurs. */
public class RemnawaveException extends RuntimeException {
    private final int statusCode;

    public RemnawaveException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public RemnawaveException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public int statusCode() { return statusCode; }
    public boolean isNotFound() { return statusCode == 404; }
}
