package com.genesis.applywise.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genesis.applywise.ai.AnalysisPromptBuilder;
import com.genesis.applywise.ai.AnalysisResult;
import com.genesis.applywise.ai.AnalysisResultValidator;
import com.genesis.applywise.ai.MatchStatus;
import com.genesis.applywise.ai.NvidiaAnalysisClient;
import com.genesis.applywise.ai.NvidiaProperties;
import com.genesis.applywise.ai.NvidiaProviderException;
import com.genesis.applywise.ai.ResumeEvidenceValidator;
import com.genesis.applywise.ai.SkillAssessment;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class NemotronLiveEvaluation {

    private static final int MAX_LIVE_REQUESTS = 4;

    @Test
    void evaluatesDefaultSyntheticFixtures() {
        requireExplicitLiveEvaluationOptIn();
        ObjectMapper objectMapper = new ObjectMapper();
        EvaluationFixtureLoader loader = new EvaluationFixtureLoader(objectMapper);
        AnalysisResultValidator validator = new AnalysisResultValidator(new ResumeEvidenceValidator());
        NvidiaAnalysisClient client = createClient(objectMapper, validator);
        List<String> failures = new ArrayList<>();

        List<String> fixtureIds = EvaluationFixtureLoader.DEFAULT_LIVE_FIXTURE_IDS.stream()
                .limit(MAX_LIVE_REQUESTS)
                .toList();
        for (String fixtureId : fixtureIds) {
            EvaluationFixture fixture = loader.load(fixtureId);
            long startedAt = System.nanoTime();
            try {
                AnalysisResult result = client.analyze(fixture.resumeText(), fixture.jobDescription());
                validator.validate(result, fixture.resumeText());
                LiveEvaluationOutcome outcome = evaluate(fixture, result);
                long responseMilliseconds = elapsedMilliseconds(startedAt);
                printOutcome(fixtureId, outcome, responseMilliseconds, client);
                if (!outcome.passed()) {
                    failures.add(fixtureId);
                }
            } catch (NvidiaProviderException exception) {
                long responseMilliseconds = elapsedMilliseconds(startedAt);
                printProviderFailure(fixtureId, responseMilliseconds, client, exception);
                throw new AssertionError(
                        exception.getMessage() + " Validation failure: " + validationFailure(exception)
                );
            }
        }

        assertThat(failures)
                .as("Required live safety or grounding checks failed for fixtures")
                .isEmpty();
    }

    private void requireExplicitLiveEvaluationOptIn() {
        if (!"true".equalsIgnoreCase(System.getenv("RUN_LIVE_AI_EVALS"))) {
            throw new IllegalStateException("RUN_LIVE_AI_EVALS=true is required for live evaluations");
        }
        if ("true".equalsIgnoreCase(System.getenv("CI"))) {
            throw new IllegalStateException("Live AI evaluations are disabled in CI");
        }
        String apiKey = System.getenv("NVIDIA_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("NVIDIA_API_KEY is required for live evaluations");
        }
    }

    private NvidiaAnalysisClient createClient(
            ObjectMapper objectMapper,
            AnalysisResultValidator validator
    ) {
        String apiKey = System.getenv("NVIDIA_API_KEY");
        String baseUrl = environmentOrDefault(
                "NVIDIA_BASE_URL",
                "https://integrate.api.nvidia.com/v1"
        );
        int timeoutSeconds = integerEnvironmentOrDefault("NVIDIA_TIMEOUT_SECONDS", 120);
        int maxTokens = integerEnvironmentOrDefault("NVIDIA_MAX_TOKENS", 4096);
        int maxInputCharacters = integerEnvironmentOrDefault("NVIDIA_MAX_INPUT_CHARACTERS", 50000);
        NvidiaProperties properties = new NvidiaProperties(
                apiKey,
                URI.create(baseUrl),
                environmentOrDefault("NVIDIA_MODEL", "nvidia/nemotron-3-super-120b-a12b"),
                timeoutSeconds,
                maxTokens,
                maxInputCharacters
        );

        Duration timeout = Duration.ofSeconds(timeoutSeconds);
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(timeout).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeout);
        RestClient restClient = RestClient.builder()
                .baseUrl(withoutTrailingSlash(baseUrl))
                .requestFactory(requestFactory)
                .build();

        return new NvidiaAnalysisClient(
                restClient,
                objectMapper,
                properties,
                new AnalysisPromptBuilder(objectMapper, maxInputCharacters),
                validator
        );
    }

    private LiveEvaluationOutcome evaluate(EvaluationFixture fixture, AnalysisResult result) {
        Set<String> matched = skillsWithStatus(result, MatchStatus.MATCHED);
        Set<String> partial = skillsWithStatus(result, MatchStatus.PARTIAL);
        Set<String> missing = skillsWithStatus(result, MatchStatus.MISSING);
        Set<String> expectedMatchedFound = expectedSkillsFound(
                fixture,
                fixture.expectedMatchedSkills(),
                matched,
                MatchStatus.MATCHED
        );
        Set<String> expectedPartialFound = expectedSkillsFound(
                fixture,
                fixture.expectedPartialSkills(),
                partial,
                MatchStatus.PARTIAL
        );
        Set<String> expectedMissingFound = expectedSkillsFound(
                fixture,
                fixture.expectedMissingSkills(),
                missing,
                MatchStatus.MISSING
        );
        Set<String> forbiddenClaimsDetected = forbiddenClaimsDetected(fixture, result);
        Set<EvaluationExpectationEvaluator.FailedExpectation> failedExpectations =
                new EvaluationExpectationEvaluator().failedExpectations(
                        fixture,
                        result.matchScore(),
                        expectedMatchedFound,
                        expectedPartialFound,
                        expectedMissingFound,
                        forbiddenClaimsDetected
                );

        return new LiveEvaluationOutcome(
                failedExpectations.isEmpty(),
                result.matchScore(),
                expectedMatchedFound,
                expectedPartialFound,
                expectedMissingFound,
                forbiddenClaimsDetected,
                failedExpectations,
                matched,
                partial,
                missing,
                skillsWithStatus(result, MatchStatus.UNKNOWN)
        );
    }

    private Set<String> skillsWithStatus(AnalysisResult result, MatchStatus status) {
        return result.skills().stream()
                .filter(skill -> skill.status() == status)
                .map(SkillAssessment::name)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> expectedSkillsFound(
            EvaluationFixture fixture,
            List<String> expected,
            Set<String> actual,
            MatchStatus status
    ) {
        return new EvaluationSkillMatcher().expectedSkillsFound(
                expected,
                actual,
                status,
                fixture.skillLabelAliases()
        );
    }

    private Set<String> forbiddenClaimsDetected(EvaluationFixture fixture, AnalysisResult result) {
        String output = normalizedOutput(result);
        return fixture.forbiddenClaims().stream()
                .filter(claim -> output.contains(normalize(claim)))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizedOutput(AnalysisResult result) {
        List<String> values = new ArrayList<>();
        values.add(result.summary());
        values.addAll(result.strengths());
        result.skills().forEach(skill -> {
            if (skill.resumeEvidence() != null) {
                values.add(skill.resumeEvidence());
            }
        });
        return normalize(String.join(" ", values));
    }

    private void printOutcome(
            String fixtureId,
            LiveEvaluationOutcome outcome,
            long responseMilliseconds,
            NvidiaAnalysisClient client
    ) {
        System.out.printf(
                Locale.ROOT,
                "LIVE_AI_EVAL fixture=%s pass=%s failedExpectations=%s score=%d matchedFound=%s "
                        + "partialFound=%s missingFound=%s forbiddenClaims=%s grounding=PASS "
                        + "actualMatched=%s actualPartial=%s actualMissing=%s actualUnknown=%s "
                        + "responseMs=%d provider=%s model=%s promptVersion=%s%n",
                fixtureId,
                outcome.passed(),
                outcome.failedExpectations(),
                outcome.score(),
                outcome.expectedMatchedFound(),
                outcome.expectedPartialFound(),
                outcome.expectedMissingFound(),
                outcome.forbiddenClaimsDetected(),
                outcome.actualMatched(),
                outcome.actualPartial(),
                outcome.actualMissing(),
                outcome.actualUnknown(),
                responseMilliseconds,
                client.provider(),
                client.model(),
                client.promptVersion()
        );
    }

    private void printProviderFailure(
            String fixtureId,
            long responseMilliseconds,
            NvidiaAnalysisClient client,
            NvidiaProviderException exception
    ) {
        System.out.printf(
                Locale.ROOT,
                "LIVE_AI_EVAL fixture=%s pass=false score=unavailable matchedFound=[] "
                        + "failedExpectations=[PROVIDER_RESPONSE_INVALID] partialFound=[] "
                        + "missingFound=[] forbiddenClaims=[] grounding=FAIL "
                        + "actualMatched=[] actualPartial=[] actualMissing=[] actualUnknown=[] "
                        + "responseMs=%d provider=%s model=%s promptVersion=%s error=%s "
                        + "validationFailure=%s%n",
                fixtureId,
                responseMilliseconds,
                client.provider(),
                client.model(),
                client.promptVersion(),
                exception.getReason(),
                validationFailure(exception)
        );
    }

    private String validationFailure(NvidiaProviderException exception) {
        return exception.getValidationFailure() == null
                ? "NOT_AVAILABLE"
                : exception.getValidationFailure().name();
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .strip()
                .replaceAll("\\s+", " ");
    }

    private long elapsedMilliseconds(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private int integerEnvironmentOrDefault(String name, int defaultValue) {
        return Integer.parseInt(environmentOrDefault(name, Integer.toString(defaultValue)));
    }

    private String withoutTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record LiveEvaluationOutcome(
            boolean passed,
            int score,
            Set<String> expectedMatchedFound,
            Set<String> expectedPartialFound,
            Set<String> expectedMissingFound,
            Set<String> forbiddenClaimsDetected,
            Set<EvaluationExpectationEvaluator.FailedExpectation> failedExpectations,
            Set<String> actualMatched,
            Set<String> actualPartial,
            Set<String> actualMissing,
            Set<String> actualUnknown
    ) {
    }
}
