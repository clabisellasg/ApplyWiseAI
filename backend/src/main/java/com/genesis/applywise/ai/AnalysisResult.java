package com.genesis.applywise.ai;

import java.util.List;

public record AnalysisResult(
        int matchScore,
        String summary,
        List<SkillAssessment> skills,
        List<String> strengths,
        List<String> gaps,
        List<String> recommendedActions
) {
    public AnalysisResult {
        if (matchScore < 0 || matchScore > 100) {
            throw new IllegalArgumentException("matchScore must be between 0 and 100");
        }

        skills = List.copyOf(skills);
        strengths = List.copyOf(strengths);
        gaps = List.copyOf(gaps);
        recommendedActions = List.copyOf(recommendedActions);
    }
}
