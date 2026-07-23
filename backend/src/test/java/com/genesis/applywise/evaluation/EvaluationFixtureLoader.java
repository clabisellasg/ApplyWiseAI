package com.genesis.applywise.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class EvaluationFixtureLoader {

    public static final List<String> ALL_FIXTURE_IDS = List.of(
            "software-engineer-strong-match",
            "software-engineer-missing-cloud",
            "data-analyst-mixed-match",
            "it-support-partial-match",
            "prompt-injection-job-description",
            "no-clear-requirements"
    );

    public static final List<String> DEFAULT_LIVE_FIXTURE_IDS = List.of(
            "software-engineer-strong-match",
            "data-analyst-mixed-match",
            "it-support-partial-match",
            "prompt-injection-job-description"
    );

    private final ObjectMapper objectMapper;

    public EvaluationFixtureLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public EvaluationFixture load(String fixtureId) {
        String resourceName = "/evaluation/" + fixtureId + ".json";
        try (InputStream input = EvaluationFixtureLoader.class.getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IllegalArgumentException("Evaluation fixture not found: " + fixtureId);
            }
            return objectMapper.readValue(input, EvaluationFixture.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read evaluation fixture: " + fixtureId, exception);
        }
    }
}
