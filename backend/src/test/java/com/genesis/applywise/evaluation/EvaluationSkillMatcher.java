package com.genesis.applywise.evaluation;

import com.genesis.applywise.ai.MatchStatus;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class EvaluationSkillMatcher {

    Set<String> expectedSkillsFound(
            List<String> expectedSkills,
            Set<String> actualSkills,
            MatchStatus status,
            List<EvaluationFixture.SkillLabelAlias> aliasGroups
    ) {
        return expectedSkills.stream()
                .filter(expected -> actualSkills.stream()
                        .anyMatch(actual -> acceptedLabels(expected, status, aliasGroups).stream()
                                .anyMatch(accepted -> matches(accepted, actual))))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    boolean matches(String acceptedLabel, String actualSkill) {
        String accepted = normalize(acceptedLabel);
        return !accepted.isBlank() && accepted.equals(normalize(actualSkill));
    }

    private Set<String> acceptedLabels(
            String expectedSkill,
            MatchStatus status,
            List<EvaluationFixture.SkillLabelAlias> aliasGroups
    ) {
        Set<String> accepted = new LinkedHashSet<>();
        accepted.add(expectedSkill);
        aliasGroups.stream()
                .filter(group -> group.status() == status)
                .filter(group -> normalizedEquals(group.expectedSkill(), expectedSkill))
                .flatMap(group -> group.acceptedLabels().stream())
                .forEach(accepted::add);
        return accepted;
    }

    private boolean normalizedEquals(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .strip()
                .replaceAll("\\s+", " ");
    }
}
