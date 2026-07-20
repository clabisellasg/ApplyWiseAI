package com.genesis.applywise.ai;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class FakeAiAnalysisClient implements AiAnalysisClient {

    private static final int MATCHED_SCORE = 100;
    private static final int PARTIAL_SCORE = 50;
    private static final int MAX_EVIDENCE_LENGTH = 240;

    private static final List<SkillDefinition> SKILLS = List.of(
            skill("Java", List.of("java"), List.of("jvm", "kotlin")),
            skill("Spring Boot", List.of("spring\\s*-?\\s*boot"), List.of("spring framework", "spring")),
            skill("React", List.of("react(?:\\.js|js)?"), List.of("angular", "vue(?:\\.js|js)?")),
            skill("TypeScript", List.of("typescript"), List.of("javascript")),
            skill("JavaScript", List.of("javascript", "node\\.js"), List.of("typescript")),
            skill("Python", List.of("python"), List.of("scripting")),
            skill("SQL", List.of("sql"), List.of("relational database", "mysql", "postgres(?:ql)?")),
            skill("PostgreSQL", List.of("postgres(?:ql)?"), List.of("sql", "relational database")),
            skill("REST APIs", List.of("rest(?:ful)?(?:\\s+apis?)?"), List.of("apis?", "http services?", "web services?")),
            skill("Git", List.of("git", "github", "gitlab"), List.of("version control")),
            skill("Docker", List.of("docker"), List.of("containers?", "kubernetes")),
            skill("AWS", List.of("aws", "amazon web services"), List.of("cloud computing", "cloud platform")),
            skill("Power BI", List.of("power\\s*bi"), List.of("business intelligence", "data visualization")),
            skill("Networking", List.of("networking", "networks?", "tcp/ip", "dns", "dhcp"), List.of("cisco"))
    );

    @Override
    public AnalysisResult analyze(String resumeContent, String jobDescription) {
        String safeResume = resumeContent == null ? "" : resumeContent;
        String safeJob = jobDescription == null ? "" : jobDescription;

        List<SkillAssessment> assessments = SKILLS.stream()
                .filter(skill -> containsAny(safeJob, skill.directPatterns()))
                .map(skill -> assess(skill, safeResume))
                .toList();

        if (assessments.isEmpty()) {
            return new AnalysisResult(
                    0,
                    "No recognizable technical skills were found in the job description.",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of("Review the job description manually for role-specific requirements.")
            );
        }

        int matchedCount = count(assessments, MatchStatus.MATCHED);
        int partialCount = count(assessments, MatchStatus.PARTIAL);
        int missingCount = count(assessments, MatchStatus.MISSING);
        int matchScore = calculateScore(assessments);

        List<String> strengths = assessments.stream()
                .filter(assessment -> assessment.status() == MatchStatus.MATCHED)
                .map(assessment -> assessment.name() + " is supported by resume evidence.")
                .toList();
        List<String> gaps = assessments.stream()
                .filter(assessment -> assessment.status() == MatchStatus.PARTIAL
                        || assessment.status() == MatchStatus.MISSING)
                .map(FakeAiAnalysisClient::gapText)
                .toList();
        List<String> recommendedActions = assessments.stream()
                .filter(assessment -> assessment.status() == MatchStatus.PARTIAL
                        || assessment.status() == MatchStatus.MISSING)
                .map(FakeAiAnalysisClient::recommendedAction)
                .toList();

        String summary = String.format(
                Locale.ROOT,
                "The resume matches %d of %d recognized job skills, with %d partial and %d missing. Score: %d/100.",
                matchedCount,
                assessments.size(),
                partialCount,
                missingCount,
                matchScore
        );

        return new AnalysisResult(
                matchScore,
                summary,
                assessments,
                strengths,
                gaps,
                recommendedActions
        );
    }

    @Override
    public String provider() {
        return "fake";
    }

    @Override
    public String model() {
        return "keyword-matcher-v1";
    }

    @Override
    public String promptVersion() {
        return "v1";
    }

    private SkillAssessment assess(SkillDefinition skill, String resumeContent) {
        Pattern directMatch = firstMatchingPattern(resumeContent, skill.directPatterns());
        if (directMatch != null) {
            return new SkillAssessment(
                    skill.name(),
                    MatchStatus.MATCHED,
                    evidenceFor(resumeContent, directMatch),
                    "The job description and resume both contain clear evidence of " + skill.name() + "."
            );
        }

        Pattern relatedMatch = firstMatchingPattern(resumeContent, skill.relatedPatterns());
        if (relatedMatch != null) {
            return new SkillAssessment(
                    skill.name(),
                    MatchStatus.PARTIAL,
                    evidenceFor(resumeContent, relatedMatch),
                    "The resume contains related evidence, but it does not explicitly demonstrate " + skill.name() + "."
            );
        }

        return new SkillAssessment(
                skill.name(),
                MatchStatus.MISSING,
                null,
                "The job description mentions " + skill.name() + ", but no supporting resume evidence was found."
        );
    }

    private static int calculateScore(List<SkillAssessment> assessments) {
        int total = assessments.stream()
                .mapToInt(assessment -> switch (assessment.status()) {
                    case MATCHED -> MATCHED_SCORE;
                    case PARTIAL -> PARTIAL_SCORE;
                    case MISSING, UNKNOWN -> 0;
                })
                .sum();

        return Math.round((float) total / assessments.size());
    }

    private static int count(List<SkillAssessment> assessments, MatchStatus status) {
        return (int) assessments.stream()
                .filter(assessment -> assessment.status() == status)
                .count();
    }

    private static String gapText(SkillAssessment assessment) {
        if (assessment.status() == MatchStatus.PARTIAL) {
            return assessment.name() + " has related but incomplete resume evidence.";
        }
        return assessment.name() + " is requested but is not evidenced in the resume.";
    }

    private static String recommendedAction(SkillAssessment assessment) {
        if (assessment.status() == MatchStatus.PARTIAL) {
            return "Add specific, truthful " + assessment.name() + " evidence if it applies to your experience.";
        }
        return "Develop or document " + assessment.name() + " experience before claiming it in an application.";
    }

    private static String evidenceFor(String content, Pattern pattern) {
        for (String segment : content.split("(?<=[.!?])\\s+|\\R+")) {
            if (pattern.matcher(segment).find()) {
                String normalized = segment.strip().replaceAll("\\s+", " ");
                if (normalized.length() <= MAX_EVIDENCE_LENGTH) {
                    return normalized;
                }
                return normalized.substring(0, MAX_EVIDENCE_LENGTH - 1) + "…";
            }
        }
        return null;
    }

    private static boolean containsAny(String content, List<Pattern> patterns) {
        return firstMatchingPattern(content, patterns) != null;
    }

    private static Pattern firstMatchingPattern(String content, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                return pattern;
            }
        }
        return null;
    }

    private static SkillDefinition skill(String name, List<String> directTerms, List<String> relatedTerms) {
        return new SkillDefinition(
                name,
                compileTerms(directTerms),
                compileTerms(relatedTerms)
        );
    }

    private static List<Pattern> compileTerms(List<String> terms) {
        List<Pattern> patterns = new ArrayList<>();
        for (String term : terms) {
            patterns.add(Pattern.compile("(?i)(?<![\\p{L}\\p{N}])(?:" + term + ")(?![\\p{L}\\p{N}])"));
        }
        return List.copyOf(patterns);
    }

    private record SkillDefinition(
            String name,
            List<Pattern> directPatterns,
            List<Pattern> relatedPatterns
    ) {
    }
}
