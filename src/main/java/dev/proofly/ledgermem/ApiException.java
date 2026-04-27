package dev.proofly.ledgermem;

import java.io.IOException;

/** Thrown when the LedgerMem API returns a non-2xx response. */
public class ApiException extends IOException {

    private final int status;
    private final String body;

    public ApiException(int status, String message, String body) {
        super("ledgermem: " + status + " " + (message == null ? "" : message));
        this.status = status;
        this.body = body;
    }

    public int status() { return status; }
    public String body() { return body; }
}
