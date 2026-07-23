package com.genesis.applywise.evaluation;

import com.genesis.applywise.ai.MatchStatus;

import java.util.List;

public record EvaluationFixture(
        String fixtureId,
        String resumeText,
        String jobDescription,
        ScoreRange expectedScoreRange,
        List<String> expectedMatchedSkills,
        List<String> expectedPartialSkills,
        List<String> expectedMissingSkills,
        List<SkillLabelAlias> skillLabelAliases,
        List<String> forbiddenClaims,
        List<String> additionalValidationNotes
) {
    public EvaluationFixture {
        skillLabelAliases = skillLabelAliases == null ? List.of() : List.copyOf(skillLabelAliases);
    }

    public record ScoreRange(int minimum, int maximum) {
        public boolean contains(int score) {
            return score >= minimum && score <= maximum;
        }
    }

    public record SkillLabelAlias(
            MatchStatus status,
            String expectedSkill,
            List<String> acceptedLabels
    ) {
        public SkillLabelAlias {
            acceptedLabels = acceptedLabels == null ? List.of() : List.copyOf(acceptedLabels);
        }
    }
}
