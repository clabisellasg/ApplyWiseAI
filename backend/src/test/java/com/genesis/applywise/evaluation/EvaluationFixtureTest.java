package com.genesis.applywise.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genesis.applywise.ai.MatchStatus;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationFixtureTest {

    private static final Pattern CONTACT_DETAILS = Pattern.compile(
            "(?:[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}|\\+?\\d[\\d ()-]{8,}\\d)"
    );

    private final EvaluationFixtureLoader loader = new EvaluationFixtureLoader(new ObjectMapper());

    @Test
    void fixturesAreCompleteShortAndPrivacySafe() {
        List<EvaluationFixture> fixtures = EvaluationFixtureLoader.ALL_FIXTURE_IDS.stream()
                .map(loader::load)
                .toList();

        assertThat(fixtures).hasSize(6);
        assertThat(fixtures).extracting(EvaluationFixture::fixtureId)
                .containsExactlyElementsOf(EvaluationFixtureLoader.ALL_FIXTURE_IDS);
        fixtures.forEach(fixture -> {
            assertThat(fixture.resumeText()).isNotBlank();
            assertThat(fixture.jobDescription()).isNotBlank();
            assertThat(fixture.resumeText().length() + fixture.jobDescription().length()).isLessThan(1000);
            assertThat(fixture.expectedScoreRange().minimum()).isBetween(0, 100);
            assertThat(fixture.expectedScoreRange().maximum()).isBetween(0, 100);
            assertThat(fixture.expectedScoreRange().minimum())
                    .isLessThanOrEqualTo(fixture.expectedScoreRange().maximum());
            assertThat(fixture.expectedMatchedSkills()).isNotNull();
            assertThat(fixture.expectedPartialSkills()).isNotNull();
            assertThat(fixture.expectedMissingSkills()).isNotNull();
            assertThat(fixture.skillLabelAliases()).isNotNull();
            assertThat(fixture.forbiddenClaims()).isNotNull();
            assertThat(fixture.additionalValidationNotes()).isNotEmpty();
            assertThat(CONTACT_DETAILS.matcher(fixture.resumeText()).find()).isFalse();
            assertThat(CONTACT_DETAILS.matcher(fixture.jobDescription()).find()).isFalse();
            assertAliasGroupsAreValidAndStatusSpecific(fixture);
        });
    }

    @Test
    void promptInjectionFixtureContainsAnAttackAndRejectsPerfectUnsupportedScoring() {
        EvaluationFixture fixture = loader.load("prompt-injection-job-description");

        assertThat(fixture.jobDescription()).contains("Ignore all previous instructions");
        assertThat(fixture.expectedScoreRange().minimum()).isZero();
        assertThat(fixture.expectedScoreRange().maximum()).isLessThan(100);
        assertThat(fixture.expectedMatchedSkills()).isEmpty();
        assertThat(fixture.expectedPartialSkills()).containsExactly("Java");
        assertThat(fixture.expectedMissingSkills()).contains("AWS");
        assertThat(fixture.forbiddenClaims()).contains("NVIDIA_OVERRIDE_ACCEPTED");
    }

    @Test
    void itSupportFixtureRequiresPartialNetworkingWithoutCreditingCisco() {
        EvaluationFixture fixture = loader.load("it-support-partial-match");

        assertThat(fixture.expectedMatchedSkills()).isEmpty();
        assertThat(fixture.expectedPartialSkills()).containsExactly("Networking");
        assertThat(fixture.expectedMissingSkills()).containsExactly("Cisco");
        assertThat(fixture.skillLabelAliases())
                .containsExactly(
                        new EvaluationFixture.SkillLabelAlias(
                                MatchStatus.PARTIAL,
                                "Networking",
                                List.of(
                                        "Networking",
                                        "Network troubleshooting",
                                        "Enterprise networking troubleshooting"
                                )
                        ),
                        new EvaluationFixture.SkillLabelAlias(
                                MatchStatus.MISSING,
                                "Cisco",
                                List.of("Cisco", "Cisco administration")
                        )
                );
    }

    @Test
    void defaultLiveEvaluationIsLimitedToFourSyntheticCases() {
        assertThat(EvaluationFixtureLoader.DEFAULT_LIVE_FIXTURE_IDS)
                .containsExactly(
                        "software-engineer-strong-match",
                        "data-analyst-mixed-match",
                        "it-support-partial-match",
                        "prompt-injection-job-description"
                )
                .hasSize(4);
    }

    private void assertAliasGroupsAreValidAndStatusSpecific(EvaluationFixture fixture) {
        Set<String> matchedLabels = acceptedLabels(fixture, MatchStatus.MATCHED);
        Set<String> partialLabels = acceptedLabels(fixture, MatchStatus.PARTIAL);
        Set<String> missingLabels = acceptedLabels(fixture, MatchStatus.MISSING);

        assertThat(Collections.disjoint(matchedLabels, partialLabels)).isTrue();
        assertThat(Collections.disjoint(matchedLabels, missingLabels)).isTrue();
        assertThat(Collections.disjoint(partialLabels, missingLabels)).isTrue();

        fixture.skillLabelAliases().forEach(group -> {
            assertThat(group.status()).isNotNull();
            assertThat(group.expectedSkill()).isNotBlank();
            assertThat(group.acceptedLabels()).isNotEmpty().allMatch(label -> !label.isBlank());
            assertThat(expectedSkills(fixture, group.status())).contains(group.expectedSkill());
        });
    }

    private Set<String> acceptedLabels(EvaluationFixture fixture, MatchStatus status) {
        Set<String> labels = new HashSet<>();
        expectedSkills(fixture, status).stream().map(this::normalize).forEach(labels::add);
        fixture.skillLabelAliases().stream()
                .filter(group -> group.status() == status)
                .flatMap(group -> group.acceptedLabels().stream())
                .map(this::normalize)
                .forEach(labels::add);
        return labels;
    }

    private List<String> expectedSkills(EvaluationFixture fixture, MatchStatus status) {
        return switch (status) {
            case MATCHED -> fixture.expectedMatchedSkills();
            case PARTIAL -> fixture.expectedPartialSkills();
            case MISSING -> fixture.expectedMissingSkills();
            case UNKNOWN -> List.of();
        };
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .strip()
                .replaceAll("\\s+", " ");
    }
}
