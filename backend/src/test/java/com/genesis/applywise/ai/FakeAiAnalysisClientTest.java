package com.genesis.applywise.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FakeAiAnalysisClientTest {

    private FakeAiAnalysisClient client;

    @BeforeEach
    void setUp() {
        client = new FakeAiAnalysisClient();
    }

    @Test
    void returnsIdenticalResultsForIdenticalInputs() {
        String resume = "Built Java and Spring Boot REST APIs backed by PostgreSQL.";
        String job = "Seeking Java, Spring Boot, PostgreSQL, and Docker experience.";

        AnalysisResult first = client.analyze(resume, job);
        AnalysisResult second = client.analyze(resume, job);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void detectsMatchedAndMissingSkillsWithoutConfusingJavaScriptWithJava() {
        AnalysisResult result = client.analyze(
                "Built production Java and Spring Boot services. Used JavaScript for browser features.",
                "This role requires Java, Spring Boot, and Docker."
        );

        assertThat(result.skills())
                .filteredOn(skill -> skill.name().equals("Java"))
                .singleElement()
                .satisfies(skill -> {
                    assertThat(skill.status()).isEqualTo(MatchStatus.MATCHED);
                    assertThat(skill.resumeEvidence()).contains("Java");
                });
        assertThat(result.skills())
                .filteredOn(skill -> skill.name().equals("Spring Boot"))
                .extracting(SkillAssessment::status)
                .containsExactly(MatchStatus.MATCHED);
        assertThat(result.skills())
                .filteredOn(skill -> skill.name().equals("Docker"))
                .singleElement()
                .satisfies(skill -> {
                    assertThat(skill.status()).isEqualTo(MatchStatus.MISSING);
                    assertThat(skill.resumeEvidence()).isNull();
                });
        assertThat(result.matchScore()).isEqualTo(67);
    }

    @Test
    void usesPartialOnlyForRelatedResumeEvidence() {
        AnalysisResult result = client.analyze(
                "Developed JavaScript applications.",
                "TypeScript experience is required."
        );

        assertThat(result.skills()).singleElement().satisfies(skill -> {
            assertThat(skill.name()).isEqualTo("TypeScript");
            assertThat(skill.status()).isEqualTo(MatchStatus.PARTIAL);
            assertThat(skill.resumeEvidence()).contains("JavaScript");
        });
        assertThat(result.matchScore()).isEqualTo(50);
    }

    @Test
    void handlesJobDescriptionsWithoutRecognizedTechnicalSkills() {
        AnalysisResult result = client.analyze(
                "Experienced customer support professional.",
                "Clear written communication and attention to detail are required."
        );

        assertThat(result.matchScore()).isZero();
        assertThat(result.skills()).isEmpty();
        assertThat(result.strengths()).isEmpty();
        assertThat(result.gaps()).isEmpty();
        assertThat(result.summary()).contains("No recognizable technical skills");
        assertThat(result.recommendedActions()).containsExactly(
                "Review the job description manually for role-specific requirements."
        );
    }

    @Test
    void recognizesEverySkillInTheSupportedCatalog() {
        AnalysisResult result = client.analyze(
                "",
                "Java, Spring Boot, React, TypeScript, JavaScript, Python, SQL, PostgreSQL, "
                        + "REST APIs, Git, Docker, AWS, Power BI, and networking."
        );

        assertThat(result.skills()).extracting(SkillAssessment::name).containsExactly(
                "Java",
                "Spring Boot",
                "React",
                "TypeScript",
                "JavaScript",
                "Python",
                "SQL",
                "PostgreSQL",
                "REST APIs",
                "Git",
                "Docker",
                "AWS",
                "Power BI",
                "Networking"
        );
        assertThat(result.skills()).extracting(SkillAssessment::status)
                .containsOnly(MatchStatus.MISSING);
    }
}
