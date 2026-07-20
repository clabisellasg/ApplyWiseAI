package com.genesis.applywise.analysis;

import com.genesis.applywise.ai.AnalysisResult;
import com.genesis.applywise.ai.MatchStatus;
import com.genesis.applywise.ai.SkillAssessment;
import com.genesis.applywise.job.CreateJobRequest;
import com.genesis.applywise.job.JobPosting;
import com.genesis.applywise.job.JobPostingRepository;
import com.genesis.applywise.job.JobPostingResponse;
import com.genesis.applywise.job.JobPostingService;
import com.genesis.applywise.resume.CreateResumeRequest;
import com.genesis.applywise.resume.Resume;
import com.genesis.applywise.resume.ResumeRepository;
import com.genesis.applywise.resume.ResumeResponse;
import com.genesis.applywise.resume.ResumeService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class AnalysisPersistenceIntegrationTest {

    @Autowired
    private AnalysisRepository analysisRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void migrationCreatesJsonbTableAndRepositoryRoundTripsStructuredResult() {
        SourceRecords sources = createSources();
        AnalysisResult result = result();
        Analysis saved = analysisRepository.saveAndFlush(new Analysis(
                sources.resume(),
                sources.jobPosting(),
                result,
                "fake",
                "keyword-matcher-v1",
                "v1"
        ));
        Long id = saved.getId();
        entityManager.clear();

        String dataType = jdbcTemplate.queryForObject("""
                SELECT data_type
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND table_name = 'analyses'
                  AND column_name = 'result_json'
                """, String.class);
        Analysis reloaded = analysisRepository.findById(id).orElseThrow();

        assertThat(dataType).isEqualTo("jsonb");
        assertThat(reloaded.getResult()).isEqualTo(result);
        assertThat(reloaded.getMatchScore()).isEqualTo(50);
        assertThat(reloaded.getResume().getId()).isEqualTo(sources.resume().getId());
        assertThat(reloaded.getJobPosting().getId()).isEqualTo(sources.jobPosting().getId());
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }

    @Test
    void foreignKeysRestrictDeletingBothKindsOfSourceRecord() {
        Map<String, String> deleteRules = jdbcTemplate.query("""
                        SELECT constraint_name, delete_rule
                        FROM information_schema.referential_constraints
                        WHERE constraint_schema = 'public'
                          AND constraint_name IN ('fk_analyses_resume', 'fk_analyses_job_posting')
                        """,
                resultSet -> {
                    Map<String, String> rules = new java.util.HashMap<>();
                    while (resultSet.next()) {
                        rules.put(resultSet.getString("constraint_name"), resultSet.getString("delete_rule"));
                    }
                    return rules;
                });

        assertThat(deleteRules).containsExactlyInAnyOrderEntriesOf(Map.of(
                "fk_analyses_resume", "RESTRICT",
                "fk_analyses_job_posting", "RESTRICT"
        ));
    }

    private SourceRecords createSources() {
        ResumeResponse resumeResponse = resumeService.create(new CreateResumeRequest(
                "Primary Resume",
                "Software Engineer",
                "Built Java and Spring Boot services."
        ));
        JobPostingResponse jobResponse = jobPostingService.create(new CreateJobRequest(
                "Backend Engineer",
                "Genesis",
                "Java, Spring Boot, and Docker required.",
                null
        ));
        Resume resume = resumeRepository.findById(resumeResponse.id()).orElseThrow();
        JobPosting jobPosting = jobPostingRepository.findById(jobResponse.id()).orElseThrow();
        return new SourceRecords(resume, jobPosting);
    }

    private AnalysisResult result() {
        return new AnalysisResult(
                50,
                "One matched skill and one missing skill.",
                List.of(
                        new SkillAssessment("Java", MatchStatus.MATCHED, "Built Java services.", "Matched."),
                        new SkillAssessment("Docker", MatchStatus.MISSING, null, "Missing.")
                ),
                List.of("Java is supported by resume evidence."),
                List.of("Docker is requested but is not evidenced in the resume."),
                List.of("Develop or document Docker experience before claiming it in an application.")
        );
    }

    private record SourceRecords(Resume resume, JobPosting jobPosting) {
    }
}
