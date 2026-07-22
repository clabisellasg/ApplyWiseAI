package com.genesis.applywise.common.exception;

import com.genesis.applywise.ai.NvidiaProviderException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceNotFound(ResourceNotFoundException exception) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        ApiErrorResponse response = new ApiErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                exception.getMessage(),
                Instant.now()
        );

        return ResponseEntity.status(status).body(response);
    }

    @ExceptionHandler(NvidiaProviderException.class)
    public ResponseEntity<ApiErrorResponse> handleNvidiaProvider(NvidiaProviderException exception) {
        HttpStatus status = exception.getResponseStatus();
        ApiErrorResponse response = new ApiErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                exception.getMessage(),
                Instant.now()
        );

        return ResponseEntity.status(status).body(response);
    }
}
