package com.genesis.applywise.application;

import com.genesis.applywise.analysis.AnalysisRepository;
import com.genesis.applywise.analysis.AnalysisResponse;
import com.genesis.applywise.analysis.AnalysisService;
import com.genesis.applywise.analysis.CreateAnalysisRequest;
import com.genesis.applywise.common.exception.ConflictException;
import com.genesis.applywise.job.CreateJobRequest;
import com.genesis.applywise.job.JobPostingRepository;
import com.genesis.applywise.job.JobPostingResponse;
import com.genesis.applywise.job.JobPostingService;
import com.genesis.applywise.resume.CreateResumeRequest;
import com.genesis.applywise.resume.ResumeRepository;
import com.genesis.applywise.resume.ResumeResponse;
import com.genesis.applywise.resume.ResumeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "ai.provider=fake")
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class JobApplicationPersistenceIntegrationTest {

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JobApplicationRepository jobApplicationRepository;

    @Autowired
    private ApplicationStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private AnalysisRepository analysisRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdApplicationIds = new ArrayList<>();
    private final List<Long> createdAnalysisIds = new ArrayList<>();
    private final List<Long> createdResumeIds = new ArrayList<>();
    private final List<Long> createdJobIds = new ArrayList<>();

    @AfterEach
    void cleanUpSyntheticRecords() {
        createdApplicationIds.forEach(id ->
                jdbcTemplate.update("DELETE FROM job_applications WHERE id = ?", id));
        createdAnalysisIds.forEach(id ->
                jdbcTemplate.update("DELETE FROM analyses WHERE id = ?", id));
        createdResumeIds.forEach(id ->
                jdbcTemplate.update("DELETE FROM resumes WHERE id = ?", id));
        createdJobIds.forEach(id ->
                jdbcTemplate.update("DELETE FROM job_postings WHERE id = ?", id));
    }

    @Test
    void migrationCreatesConstraintsAndPersistsCompleteApplication() {
        SourceRecords sources = createSources("Complete");

        JobApplicationResponse response = track(jobApplicationService.create(
                new CreateApplicationRequest(
                        sources.job().id(),
                        sources.resume().id(),
                        sources.analysis().id(),
                        ApplicationStatus.SAVED,
                        null,
                        "Review interview topics",
                        null,
                        "Synthetic integration record"
                )
        ));

        Long tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('job_applications', 'application_status_history')
                """, Long.class);
        Long uniqueConstraintCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.table_constraints
                WHERE constraint_schema = 'public'
                  AND table_name = 'job_applications'
                  AND constraint_name = 'uq_job_applications_job_posting'
                  AND constraint_type = 'UNIQUE'
                """, Long.class);
        Map<String, String> deleteRules = foreignKeyDeleteRules();

        assertThat(tableCount).isEqualTo(2L);
        assertThat(uniqueConstraintCount).isEqualTo(1L);
        assertThat(deleteRules).containsAllEntriesOf(Map.of(
                "fk_job_applications_job_posting", "RESTRICT",
                "fk_job_applications_resume", "SET NULL",
                "fk_job_applications_latest_analysis", "SET NULL",
                "fk_application_history_application", "CASCADE"
        ));
        assertThat(response.job().id()).isEqualTo(sources.job().id());
        assertThat(response.resume().id()).isEqualTo(sources.resume().id());
        assertThat(response.analysis().id()).isEqualTo(sources.analysis().id());
        assertThat(jobApplicationService.findHistory(response.id()))
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.previousStatus()).isNull();
                    assertThat(history.newStatus()).isEqualTo(ApplicationStatus.SAVED);
                });
    }

    @Test
    void statusChangesAreAtomicOrderedAndDoNotDuplicateSameStatus() {
        JobPostingResponse job = createJob("History");
        JobApplicationResponse created = track(jobApplicationService.create(
                new CreateApplicationRequest(
                        job.id(),
                        null,
                        null,
                        ApplicationStatus.SAVED,
                        null,
                        null,
                        null,
                        null
                )
        ));

        JobApplicationResponse applied = jobApplicationService.updateStatus(
                created.id(),
                new UpdateApplicationStatusRequest(ApplicationStatus.APPLIED)
        );
        jobApplicationService.updateStatus(
                created.id(),
                new UpdateApplicationStatusRequest(ApplicationStatus.APPLIED)
        );
        JobApplicationResponse interviewed = jobApplicationService.updateStatus(
                created.id(),
                new UpdateApplicationStatusRequest(ApplicationStatus.INTERVIEW)
        );
        List<ApplicationStatusHistoryResponse> history =
                jobApplicationService.findHistory(created.id());

        assertThat(applied.appliedAt()).isEqualTo(LocalDate.now(java.time.ZoneOffset.UTC));
        assertThat(interviewed.appliedAt()).isEqualTo(applied.appliedAt());
        assertThat(history).hasSize(3);
        assertThat(history).extracting(ApplicationStatusHistoryResponse::newStatus)
                .containsExactly(
                        ApplicationStatus.INTERVIEW,
                        ApplicationStatus.APPLIED,
                        ApplicationStatus.SAVED
                );
    }

    @Test
    void deletingApplicationPreservesJobResumeAndAnalysis() {
        SourceRecords sources = createSources("Delete application");
        JobApplicationResponse application = track(jobApplicationService.create(
                new CreateApplicationRequest(
                        sources.job().id(),
                        sources.resume().id(),
                        sources.analysis().id(),
                        null,
                        null,
                        null,
                        null,
                        null
                )
        ));

        jobApplicationService.delete(application.id());

        assertThat(jobApplicationRepository.findById(application.id())).isEmpty();
        assertThat(statusHistoryRepository
                .findByJobApplicationIdOrderByChangedAtDescIdDesc(application.id()))
                .isEmpty();
        assertThat(jobPostingRepository.existsById(sources.job().id())).isTrue();
        assertThat(resumeRepository.existsById(sources.resume().id())).isTrue();
        assertThat(analysisRepository.existsById(sources.analysis().id())).isTrue();
    }

    @Test
    void deletingOptionalResumeOrAnalysisClearsOnlyThatApplicationReference() {
        JobPostingResponse resumeOnlyJob = createJob("Resume nulling");
        ResumeResponse resumeOnly = createResume("Resume nulling");
        JobApplicationResponse resumeApplication = track(jobApplicationService.create(
                new CreateApplicationRequest(
                        resumeOnlyJob.id(),
                        resumeOnly.id(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        ));

        resumeService.delete(resumeOnly.id());

        assertThat(jobApplicationService.findById(resumeApplication.id()).resume()).isNull();
        assertThat(jobPostingRepository.existsById(resumeOnlyJob.id())).isTrue();

        SourceRecords analysisSources = createSources("Analysis nulling");
        JobApplicationResponse analysisApplication = track(jobApplicationService.create(
                new CreateApplicationRequest(
                        analysisSources.job().id(),
                        analysisSources.resume().id(),
                        analysisSources.analysis().id(),
                        null,
                        null,
                        null,
                        null,
                        null
                )
        ));

        analysisRepository.deleteById(analysisSources.analysis().id());

        JobApplicationResponse reloaded =
                jobApplicationService.findById(analysisApplication.id());
        assertThat(reloaded.analysis()).isNull();
        assertThat(reloaded.resume().id()).isEqualTo(analysisSources.resume().id());
        assertThat(jobPostingRepository.existsById(analysisSources.job().id())).isTrue();
    }

    @Test
    void trackedJobCannotBeDeletedAndDuplicateTrackingIsRejected() {
        JobPostingResponse job = createJob("Protected");
        track(jobApplicationService.create(new CreateApplicationRequest(
                job.id(), null, null, null, null, null, null, null
        )));

        assertThatThrownBy(() -> jobApplicationService.create(
                new CreateApplicationRequest(
                        job.id(), null, null, null, null, null, null, null
                )
        ))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Job posting " + job.id() + " is already tracked as an application.");
        assertThatThrownBy(() -> jobPostingService.delete(job.id()))
                .isInstanceOf(ConflictException.class)
                .hasMessage(
                        "Job posting " + job.id()
                                + " cannot be deleted while it has a tracked application."
                );
        assertThat(jobPostingRepository.existsById(job.id())).isTrue();
    }

    private Map<String, String> foreignKeyDeleteRules() {
        return jdbcTemplate.query("""
                        SELECT constraint_name, delete_rule
                        FROM information_schema.referential_constraints
                        WHERE constraint_schema = 'public'
                          AND constraint_name IN (
                            'fk_job_applications_job_posting',
                            'fk_job_applications_resume',
                            'fk_job_applications_latest_analysis',
                            'fk_application_history_application'
                          )
                        """,
                resultSet -> {
                    Map<String, String> rules = new HashMap<>();
                    while (resultSet.next()) {
                        rules.put(
                                resultSet.getString("constraint_name"),
                                resultSet.getString("delete_rule")
                        );
                    }
                    return rules;
                });
    }

    private SourceRecords createSources(String suffix) {
        ResumeResponse resume = createResume(suffix);
        JobPostingResponse job = createJob(suffix);
        AnalysisResponse analysis = analysisService.create(
                new CreateAnalysisRequest(resume.id(), job.id())
        );
        createdAnalysisIds.add(analysis.id());
        return new SourceRecords(job, resume, analysis);
    }

    private JobPostingResponse createJob(String suffix) {
        JobPostingResponse response = jobPostingService.create(new CreateJobRequest(
                "Support Engineer " + suffix,
                "Synthetic Company",
                "Java and networking experience required for " + suffix + ".",
                null
        ));
        createdJobIds.add(response.id());
        return response;
    }

    private ResumeResponse createResume(String suffix) {
        ResumeResponse response = resumeService.create(new CreateResumeRequest(
                "Resume " + suffix,
                "IT Support",
                "Resolved networking issues and built Java tools for " + suffix + "."
        ));
        createdResumeIds.add(response.id());
        return response;
    }

    private JobApplicationResponse track(JobApplicationResponse response) {
        createdApplicationIds.add(response.id());
        return response;
    }

    private record SourceRecords(
            JobPostingResponse job,
            ResumeResponse resume,
            AnalysisResponse analysis
    ) {
    }
}
