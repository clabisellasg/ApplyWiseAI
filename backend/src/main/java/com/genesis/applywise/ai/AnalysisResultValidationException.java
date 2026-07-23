package com.genesis.applywise.ai;

public class AnalysisResultValidationException extends RuntimeException {

    private final AnalysisValidationFailure failure;

    public AnalysisResultValidationException(AnalysisValidationFailure failure, String message) {
        super(message);
        this.failure = failure;
    }

    public AnalysisValidationFailure getFailure() {
        return failure;
    }
}
