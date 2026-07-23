package com.genesis.applywise.ai;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public class ResumeEvidenceValidator {

    private static final int MAX_EVIDENCE_CHARACTERS = 300;
    private static final Pattern NON_WORD_CHARACTERS = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public void validate(String evidence, String resumeContent) {
        if (evidence == null || evidence.isBlank()) {
            throw new AnalysisResultValidationException(
                    AnalysisValidationFailure.MISSING_REQUIRED_EVIDENCE,
                    "Matched or partial skill evidence is missing"
            );
        }
        if (evidence.length() > MAX_EVIDENCE_CHARACTERS) {
            throw new AnalysisResultValidationException(
                    AnalysisValidationFailure.UNSUPPORTED_EVIDENCE,
                    "Resume evidence is not a short excerpt"
            );
        }

        String normalizedEvidence = normalize(evidence);
        String normalizedResume = normalize(resumeContent);
        if (normalizedEvidence.isBlank() || !normalizedResume.contains(normalizedEvidence)) {
            throw new AnalysisResultValidationException(
                    AnalysisValidationFailure.UNSUPPORTED_EVIDENCE,
                    "Resume evidence is not grounded in the supplied resume"
            );
        }
    }

    String normalize(String value) {
        if (value == null) {
            return "";
        }
        String compatible = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        String withoutPunctuation = NON_WORD_CHARACTERS.matcher(compatible).replaceAll(" ");
        return WHITESPACE.matcher(withoutPunctuation.strip()).replaceAll(" ");
    }
}
