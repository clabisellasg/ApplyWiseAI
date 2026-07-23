package com.genesis.applywise.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genesis.applywise.ai.MatchStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationSkillMatcherTest {

    private final EvaluationSkillMatcher matcher = new EvaluationSkillMatcher();

    @Test
    void acceptsOnlyExplicitAliasesForTheExpectedStatus() {
        List<EvaluationFixture.SkillLabelAlias> aliases = List.of(
                new EvaluationFixture.SkillLabelAlias(
                        MatchStatus.PARTIAL,
                        "Networking",
                        List.of("Networking", "Network troubleshooting")
                )
        );

        assertThat(matcher.expectedSkillsFound(
                List.of("Networking"),
                Set.of("network troubleshooting"),
                MatchStatus.PARTIAL,
                aliases
        )).containsExactly("Networking");

        assertThat(matcher.expectedSkillsFound(
                List.of("Networking"),
                Set.of("network troubleshooting"),
                MatchStatus.MATCHED,
                aliases
        )).isEmpty();
    }

    @Test
    void acceptsTheItSupportLabelsOnlyThroughTheirFixtureAliases() {
        EvaluationFixture fixture = new EvaluationFixtureLoader(new ObjectMapper())
                .load("it-support-partial-match");

        assertThat(matcher.expectedSkillsFound(
                fixture.expectedPartialSkills(),
                Set.of("network troubleshooting"),
                MatchStatus.PARTIAL,
                fixture.skillLabelAliases()
        )).containsExactly("Networking");
        assertThat(matcher.expectedSkillsFound(
                fixture.expectedMissingSkills(),
                Set.of("Cisco administration"),
                MatchStatus.MISSING,
                fixture.skillLabelAliases()
        )).containsExactly("Cisco");
        assertThat(matcher.expectedSkillsFound(
                fixture.expectedPartialSkills(),
                Set.of("enterprise networking"),
                MatchStatus.PARTIAL,
                fixture.skillLabelAliases()
        )).isEmpty();
    }

    @Test
    void doesNotUseSubstringOrUnlistedSemanticMatching() {
        assertThat(matcher.matches("Java", "JavaScript")).isFalse();
        assertThat(matcher.matches("SQL", "NoSQL")).isFalse();
        assertThat(matcher.matches("Networking", "Network troubleshooting")).isFalse();
        assertThat(matcher.matches("Networking", "Enterprise networking")).isFalse();
    }

    @Test
    void normalizesOnlyCasePunctuationAndWhitespace() {
        assertThat(matcher.matches("REST APIs", "  rest-apis ")).isTrue();
        assertThat(matcher.matches("Cisco administration", "Cisco")).isFalse();
    }
}
