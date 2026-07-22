package com.genesis.applywise.ai;

import org.springframework.http.HttpStatus;

public class NvidiaProviderException extends RuntimeException {

    private final HttpStatus responseStatus;

    public NvidiaProviderException(HttpStatus responseStatus, String message) {
        super(message);
        this.responseStatus = responseStatus;
    }

    public HttpStatus getResponseStatus() {
        return responseStatus;
    }
}
