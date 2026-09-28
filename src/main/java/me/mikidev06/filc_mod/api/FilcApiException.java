package me.mikidev06.filc_mod.api;

/** Raised when the API returns an error envelope or an unusable response. */
public class FilcApiException extends RuntimeException {
    private final String code;
    private final int statusCode;
    private final long retryAfterSeconds;

    public FilcApiException(String message, String code, int statusCode, long retryAfterSeconds) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public FilcApiException(String message, Throwable cause) {
        super(message, cause);
        this.code = "UNKNOWN";
        this.statusCode = -1;
        this.retryAfterSeconds = -1;
    }

    public String code() {
        return code;
    }

    public int statusCode() {
        return statusCode;
    }

    /** Seconds the server asked us to wait, or -1 when it did not say. */
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public boolean isRateLimited() {
        return statusCode == 429 || "RATE_LIMITED".equals(code);
    }
}
