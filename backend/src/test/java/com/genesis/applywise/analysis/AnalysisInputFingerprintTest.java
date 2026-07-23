package com.genesis.applywise.analysis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisInputFingerprintTest {

    private AnalysisInputFingerprint fingerprint;

    @BeforeEach
    void setUp() {
        fingerprint = new AnalysisInputFingerprint();
    }

    @Test
    void producesAConsistentSha256Hash() {
        String first = generate("Java resume", "Java job", "model-v1", "prompt-v1");
        String second = generate("Java resume", "Java job", "model-v1", "prompt-v1");

        assertThat(first).isEqualTo(second).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void differentResumeContentProducesDifferentHash() {
        assertThat(generate("Java resume", "Java job", "model-v1", "prompt-v1"))
                .isNotEqualTo(generate("Python resume", "Java job", "model-v1", "prompt-v1"));
    }

    @Test
    void differentJobDescriptionProducesDifferentHash() {
        assertThat(generate("Java resume", "Java job", "model-v1", "prompt-v1"))
                .isNotEqualTo(generate("Java resume", "Python job", "model-v1", "prompt-v1"));
    }

    @Test
    void differentModelOrPromptVersionDoesNotShareHash() {
        String baseline = generate("Java resume", "Java job", "model-v1", "prompt-v1");

        assertThat(baseline).isNotEqualTo(generate("Java resume", "Java job", "model-v2", "prompt-v1"));
        assertThat(baseline).isNotEqualTo(generate("Java resume", "Java job", "model-v1", "prompt-v2"));
    }

    @Test
    void lengthPrefixesPreventAmbiguousFieldConcatenation() {
        assertThat(generate("ab", "c", "model-v1", "prompt-v1"))
                .isNotEqualTo(generate("a", "bc", "model-v1", "prompt-v1"));
    }

    private String generate(String resume, String job, String model, String promptVersion) {
        return fingerprint.generate(resume, job, "nvidia", model, promptVersion);
    }
}
