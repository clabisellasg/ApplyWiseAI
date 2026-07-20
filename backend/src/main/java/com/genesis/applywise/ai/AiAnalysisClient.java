package com.genesis.applywise.ai;

public interface AiAnalysisClient {

    AnalysisResult analyze(String resumeContent, String jobDescription);

    String provider();

    String model();

    String promptVersion();
}
