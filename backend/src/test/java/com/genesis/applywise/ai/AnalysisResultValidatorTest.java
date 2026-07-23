package com.genesis.applywise.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalysisResultValidatorTest {

    private static final String RESUME = "Built Java-based services; maintained REST APIs for customers.";

    private AnalysisResultValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AnalysisResultValidator(new ResumeEvidenceValidator());
    }

    @Test
    void acceptsGroundedEvidenceDespiteCaseWhitespaceAndPunctuationDifferences() {
        AnalysisResult result = result(List.of(
                skill("Java", MatchStatus.MATCHED, "built  JAVA based services", "Direct evidence.")
        ));

        assertThatCode(() -> validator.validate(result, RESUME)).doesNotThrowAnyException();
    }

    @Test
    void rejectsFabricatedEvidence() {
        AnalysisResult result = result(List.of(
                skill("AWS", MatchStatus.MATCHED, "AWS certified architect", "Unsupported claim.")
        ));

        assertThatThrownBy(() -> validator.validate(result, RESUME))
                .isInstanceOf(AnalysisResultValidationException.class)
                .hasMessage("Resume evidence is not grounded in the supplied resume");
    }

    @Test
    void rejectsContradictoryNormalizedSkillStatuses() {
        AnalysisResult result = result(List.of(
                skill("REST APIs", MatchStatus.MATCHED, "maintained REST APIs", "Direct evidence."),
                skill("rest-apis", MatchStatus.MISSING, "", "Contradictory status.")
        ));

        assertThatThrownBy(() -> validator.validate(result, RESUME))
                .isInstanceOf(AnalysisResultValidationException.class)
                .hasMessage("Analysis contains contradictory skill statuses");
    }

    @Test
    void rejectsDuplicateNormalizedSkills() {
        AnalysisResult result = result(List.of(
                skill("Java", MatchStatus.MATCHED, "Built Java-based services", "Direct evidence."),
                skill(" java ", MatchStatus.MATCHED, "Built Java-based services", "Duplicate evidence.")
        ));

        assertThatThrownBy(() -> validator.validate(result, RESUME))
                .isInstanceOf(AnalysisResultValidationException.class)
                .hasMessage("Analysis contains duplicate skills");
    }

    @Test
    void rejectsEvidenceOnMissingSkill() {
        AnalysisResult result = result(List.of(
                skill("Docker", MatchStatus.MISSING, "Built Java-based services", "No Docker evidence.")
        ));

        assertThatThrownBy(() -> validator.validate(result, RESUME))
                .isInstanceOf(AnalysisResultValidationException.class)
                .hasMessage("Missing skill must not claim resume evidence");
    }

    @Test
    void rejectsOutOfRangeScore() {
        AnalysisResult result = mock(AnalysisResult.class);
        when(result.matchScore()).thenReturn(101);

        assertThatThrownBy(() -> validator.validate(result, RESUME))
                .isInstanceOf(AnalysisResultValidationException.class)
                .hasMessage("Match score is outside the supported range");
    }

    @Test
    void rejectsNullOrIncompleteCollections() {
        AnalysisResult result = mock(AnalysisResult.class);
        when(result.matchScore()).thenReturn(50);
        when(result.summary()).thenReturn("Summary");
        when(result.skills()).thenReturn(null);

        assertThatThrownBy(() -> validator.validate(result, RESUME))
                .isInstanceOf(AnalysisResultValidationException.class)
                .hasMessage("Skill assessments are missing");
    }

    @Test
    void rejectsRecommendationToFabricateExperience() {
        AnalysisResult result = new AnalysisResult(
                50,
                "Summary",
                List.of(),
                List.of(),
                List.of(),
                List.of("Fabricate experience with AWS before applying.")
        );

        assertThatThrownBy(() -> validator.validate(result, RESUME))
                .isInstanceOf(AnalysisResultValidationException.class)
                .hasMessage("Recommended action encourages fabricated experience");
    }

    private AnalysisResult result(List<SkillAssessment> skills) {
        return new AnalysisResult(
                50,
                "Grounded summary.",
                skills,
                List.of("Grounded strength."),
                List.of("Grounded gap."),
                List.of("Build the missing skill through a real project.")
        );
    }

    private SkillAssessment skill(String name, MatchStatus status, String evidence, String explanation) {
        return new SkillAssessment(name, status, evidence, explanation);
    }
}
