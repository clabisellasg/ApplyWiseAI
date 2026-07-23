package com.genesis.applywise.ai;

import org.springframework.http.HttpStatus;

public class NvidiaProviderException extends RuntimeException {

    public enum Reason {
        AUTHENTICATION,
        RATE_LIMIT,
        TEMPORARY_UNAVAILABLE,
        INVALID_RESPONSE,
        INVALID_INPUT,
        OTHER
    }

    private final HttpStatus responseStatus;
    private final Reason reason;
    private final AnalysisValidationFailure validationFailure;

    public NvidiaProviderException(HttpStatus responseStatus, String message) {
        this(responseStatus, Reason.OTHER, message, null);
    }

    public NvidiaProviderException(HttpStatus responseStatus, Reason reason, String message) {
        this(responseStatus, reason, message, null);
    }

    public NvidiaProviderException(
            HttpStatus responseStatus,
            Reason reason,
            String message,
            AnalysisValidationFailure validationFailure
    ) {
        super(message);
        this.responseStatus = responseStatus;
        this.reason = reason;
        this.validationFailure = validationFailure;
    }

    public HttpStatus getResponseStatus() {
        return responseStatus;
    }

    public Reason getReason() {
        return reason;
    }

    public AnalysisValidationFailure getValidationFailure() {
        return validationFailure;
    }
}
