package com.genesis.applywise.ai;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AnalysisResultValidator {

    private static final List<String> FABRICATION_PHRASES = List.of(
            "invent experience",
            "invent a qualification",
            "fabricate experience",
            "fabricate a qualification",
            "make up experience",
            "add false experience",
            "claim experience you do not have",
            "pretend to have",
            "lie about",
            "falsify"
    );

    private final ResumeEvidenceValidator evidenceValidator;

    public AnalysisResultValidator(ResumeEvidenceValidator evidenceValidator) {
        this.evidenceValidator = evidenceValidator;
    }

    public void validate(AnalysisResult result, String resumeContent) {
        if (result == null) {
            throw invalid(AnalysisValidationFailure.MALFORMED_PROVIDER_RESPONSE, "Analysis result is missing");
        }
        if (result.matchScore() < 0 || result.matchScore() > 100) {
            throw invalid(AnalysisValidationFailure.INVALID_SCORE, "Match score is outside the supported range");
        }
        requireNonBlank(
                result.summary(),
                AnalysisValidationFailure.MISSING_REQUIRED_TEXT,
                "Analysis summary is missing"
        );
        requireCollection(result.skills(), "Skill assessments are missing");
        requireCollection(result.strengths(), "Strengths are missing");
        requireCollection(result.gaps(), "Gaps are missing");
        requireCollection(result.recommendedActions(), "Recommended actions are missing");

        Map<String, MatchStatus> skillsByName = new HashMap<>();
        for (SkillAssessment skill : result.skills()) {
            validateSkill(skill, resumeContent, skillsByName);
        }

        validateTextCollection(result.strengths(), "Strength contains an empty value");
        validateTextCollection(result.gaps(), "Gap contains an empty value");
        validateTextCollection(result.recommendedActions(), "Recommended action contains an empty value");
        validateRecommendedActions(result.recommendedActions());
    }

    private void validateSkill(
            SkillAssessment skill,
            String resumeContent,
            Map<String, MatchStatus> skillsByName
    ) {
        if (skill == null) {
            throw invalid(
                    AnalysisValidationFailure.MISSING_SKILL_ASSESSMENT,
                    "Skill assessment is missing"
            );
        }
        requireNonBlank(
                skill.name(),
                AnalysisValidationFailure.BLANK_SKILL_NAME,
                "Skill name is missing"
        );
        requireNonBlank(
                skill.explanation(),
                AnalysisValidationFailure.BLANK_SKILL_EXPLANATION,
                "Skill explanation is missing"
        );
        if (skill.status() == null) {
            throw invalid(
                    AnalysisValidationFailure.UNSUPPORTED_STATUS,
                    "Skill status is missing or unsupported"
            );
        }

        String normalizedName = normalizeSkillName(skill.name());
        if (normalizedName.isBlank()) {
            throw invalid(AnalysisValidationFailure.BLANK_SKILL_NAME, "Skill name is missing");
        }
        MatchStatus previousStatus = skillsByName.putIfAbsent(normalizedName, skill.status());
        if (previousStatus != null && previousStatus != skill.status()) {
            throw invalid(
                    AnalysisValidationFailure.CONTRADICTORY_STATUS,
                    "Analysis contains contradictory skill statuses"
            );
        }
        if (previousStatus != null) {
            throw invalid(AnalysisValidationFailure.DUPLICATE_SKILL, "Analysis contains duplicate skills");
        }

        if (skill.status() == MatchStatus.MATCHED || skill.status() == MatchStatus.PARTIAL) {
            evidenceValidator.validate(skill.resumeEvidence(), resumeContent);
        } else if (skill.status() == MatchStatus.MISSING
                && skill.resumeEvidence() != null
                && !skill.resumeEvidence().isBlank()) {
            throw invalid(
                    AnalysisValidationFailure.UNSUPPORTED_EVIDENCE,
                    "Missing skill must not claim resume evidence"
            );
        } else if (skill.resumeEvidence() != null && !skill.resumeEvidence().isBlank()) {
            evidenceValidator.validate(skill.resumeEvidence(), resumeContent);
        }
    }

    private void validateTextCollection(List<String> values, String message) {
        for (String value : values) {
            requireNonBlank(value, AnalysisValidationFailure.BLANK_REQUIRED_COLLECTION_ITEM, message);
        }
    }

    private void validateRecommendedActions(List<String> actions) {
        for (String action : actions) {
            String normalized = action.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (FABRICATION_PHRASES.stream().anyMatch(normalized::contains)) {
                throw invalid(
                        AnalysisValidationFailure.UNSAFE_RECOMMENDATION,
                        "Recommended action encourages fabricated experience"
                );
            }
        }
    }

    private String normalizeSkillName(String name) {
        return Normalizer.normalize(name, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s_-]+", "");
    }

    private void requireNonBlank(
            String value,
            AnalysisValidationFailure failure,
            String message
    ) {
        if (value == null || value.isBlank()) {
            throw invalid(failure, message);
        }
    }

    private void requireCollection(List<?> values, String message) {
        if (values == null) {
            throw invalid(AnalysisValidationFailure.MISSING_REQUIRED_COLLECTION, message);
        }
    }

    private AnalysisResultValidationException invalid(
            AnalysisValidationFailure failure,
            String message
    ) {
        return new AnalysisResultValidationException(failure, message);
    }
}
