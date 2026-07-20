package com.genesis.applywise.ai;

public record SkillAssessment(
        String name,
        MatchStatus status,
        String resumeEvidence,
        String explanation
) {
}
