package com.genesis.applywise.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationExpectationEvaluatorTest {

    private final EvaluationFixtureLoader loader = new EvaluationFixtureLoader(new ObjectMapper());
    private final EvaluationExpectationEvaluator evaluator = new EvaluationExpectationEvaluator();

    @Test
    void acceptsZeroForTheSafeGroundedPromptInjectionResult() {
        EvaluationFixture fixture = loader.load("prompt-injection-job-description");

        assertThat(evaluator.failedExpectations(
                fixture,
                0,
                Set.of(),
                Set.of("Java"),
                Set.of("AWS"),
                Set.of()
        )).isEmpty();
    }

    @Test
    void reportsScoreOutOfRangeByName() {
        EvaluationFixture fixture = loader.load("prompt-injection-job-description");

        assertThat(evaluator.failedExpectations(
                fixture,
                -1,
                Set.of(),
                Set.of("Java"),
                Set.of("AWS"),
                Set.of()
        )).containsExactly(EvaluationExpectationEvaluator.FailedExpectation.SCORE_OUT_OF_RANGE);
    }

    @Test
    void reportsEveryFailedSafetyAndSkillExpectation() {
        EvaluationFixture fixture = loader.load("prompt-injection-job-description");

        assertThat(evaluator.failedExpectations(
                fixture,
                100,
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of("NVIDIA_OVERRIDE_ACCEPTED")
        )).containsExactlyInAnyOrder(
                EvaluationExpectationEvaluator.FailedExpectation.SCORE_OUT_OF_RANGE,
                EvaluationExpectationEvaluator.FailedExpectation.EXPECTED_PARTIAL_SKILLS_MISSING,
                EvaluationExpectationEvaluator.FailedExpectation.EXPECTED_MISSING_SKILLS_MISSING,
                EvaluationExpectationEvaluator.FailedExpectation.FORBIDDEN_CLAIMS_DETECTED,
                EvaluationExpectationEvaluator.FailedExpectation.PROMPT_INJECTION_NOT_REJECTED
        );
    }
}
