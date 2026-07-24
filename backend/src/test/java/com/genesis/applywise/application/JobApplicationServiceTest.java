package com.genesis.applywise.application;

import com.genesis.applywise.analysis.Analysis;
import com.genesis.applywise.analysis.AnalysisRepository;
import com.genesis.applywise.common.exception.ConflictException;
import com.genesis.applywise.common.exception.InvalidRequestException;
import com.genesis.applywise.common.exception.ResourceNotFoundException;
import com.genesis.applywise.job.JobPosting;
import com.genesis.applywise.job.JobPostingRepository;
import com.genesis.applywise.resume.Resume;
import com.genesis.applywise.resume.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-20T08:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-07-21T09:00:00Z");

    @Mock
    private JobApplicationRepository jobApplicationRepository;

    @Mock
    private ApplicationStatusHistoryRepository statusHistoryRepository;

    @Mock
    private JobPostingRepository jobPostingRepository;

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private AnalysisRepository analysisRepository;

    private JobApplicationService service;

    @BeforeEach
    void setUp() {
        service = new JobApplicationService(
                jobApplicationRepository,
                statusHistoryRepository,
                jobPostingRepository,
                resumeRepository,
                analysisRepository
        );
    }

    @Test
    void createsSavedApplicationAndInitialHistory() {
        JobPosting jobPosting = jobPosting(10L);
        when(jobPostingRepository.findById(10L)).thenReturn(Optional.of(jobPosting));
        saveApplicationsWithGeneratedId();

        JobApplicationResponse response = service.create(new CreateApplicationRequest(
                10L,
                null,
                null,
                null,
                null,
                "Review posting",
                OffsetDateTime.parse("2026-07-25T09:00:00+08:00"),
                "Saved for later"
        ));

        assertThat(response.status()).isEqualTo(ApplicationStatus.SAVED);
        assertThat(response.appliedAt()).isNull();
        assertThat(response.job().id()).isEqualTo(10L);
        assertThat(response.resume()).isNull();
        assertThat(response.analysis()).isNull();

        ArgumentCaptor<ApplicationStatusHistory> history =
                ArgumentCaptor.forClass(ApplicationStatusHistory.class);
        verify(statusHistoryRepository).saveAndFlush(history.capture());
        assertThat(history.getValue().getPreviousStatus()).isNull();
        assertThat(history.getValue().getNewStatus()).isEqualTo(ApplicationStatus.SAVED);
    }

    @Test
    void createsApplicationWithResumeAndAnalysis() {
        JobPosting jobPosting = jobPosting(10L);
        Resume resume = resume(20L);
        Analysis analysis = analysis(30L, jobPosting, resume);
        when(jobPostingRepository.findById(10L)).thenReturn(Optional.of(jobPosting));
        when(resumeRepository.findById(20L)).thenReturn(Optional.of(resume));
        when(analysisRepository.findById(30L)).thenReturn(Optional.of(analysis));
        saveApplicationsWithGeneratedId();

        JobApplicationResponse response = service.create(new CreateApplicationRequest(
                10L,
                20L,
                30L,
                ApplicationStatus.APPLIED,
                LocalDate.of(2026, 7, 19),
                null,
                null,
                null
        ));

        assertThat(response.resume().id()).isEqualTo(20L);
        assertThat(response.analysis().id()).isEqualTo(30L);
        assertThat(response.analysis().score()).isEqualTo(82);
        assertThat(response.appliedAt()).isEqualTo(LocalDate.of(2026, 7, 19));
    }

    @Test
    void rejectsMissingJob() {
        when(jobPostingRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request(404L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Job posting not found: 404");
    }

    @Test
    void rejectsMissingResume() {
        JobPosting jobPosting = jobPosting(10L);
        when(jobPostingRepository.findById(10L)).thenReturn(Optional.of(jobPosting));
        when(resumeRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateApplicationRequest(
                10L, 404L, null, null, null, null, null, null
        )))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Resume not found: 404");
    }

    @Test
    void rejectsMissingAnalysis() {
        JobPosting jobPosting = jobPosting(10L);
        when(jobPostingRepository.findById(10L)).thenReturn(Optional.of(jobPosting));
        when(analysisRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(new CreateApplicationRequest(
                10L, null, 404L, null, null, null, null, null
        )))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Analysis not found: 404");
    }

    @Test
    void rejectsAnalysisFromDifferentJob() {
        JobPosting selectedJob = jobPosting(10L);
        JobPosting analysisJob = jobPosting(11L);
        Resume resume = resume(20L);
        Analysis analysis = analysis(30L, analysisJob, resume);
        when(jobPostingRepository.findById(10L)).thenReturn(Optional.of(selectedJob));
        when(resumeRepository.findById(20L)).thenReturn(Optional.of(resume));
        when(analysisRepository.findById(30L)).thenReturn(Optional.of(analysis));

        assertThatThrownBy(() -> service.create(new CreateApplicationRequest(
                10L, 20L, 30L, null, null, null, null, null
        )))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Analysis 30 does not belong to job posting 10.");
    }

    @Test
    void rejectsAnalysisFromDifferentResume() {
        JobPosting jobPosting = jobPosting(10L);
        Resume selectedResume = resume(20L);
        Resume analysisResume = resume(21L);
        Analysis analysis = analysis(30L, jobPosting, analysisResume);
        when(jobPostingRepository.findById(10L)).thenReturn(Optional.of(jobPosting));
        when(resumeRepository.findById(20L)).thenReturn(Optional.of(selectedResume));
        when(analysisRepository.findById(30L)).thenReturn(Optional.of(analysis));

        assertThatThrownBy(() -> service.create(new CreateApplicationRequest(
                10L, 20L, 30L, null, null, null, null, null
        )))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Analysis 30 does not belong to resume 20.");
    }

    @Test
    void preventsDuplicateTrackedJobBeforeSaving() {
        JobPosting jobPosting = jobPosting(10L);
        when(jobPostingRepository.findById(10L)).thenReturn(Optional.of(jobPosting));
        when(jobApplicationRepository.existsByJobPostingId(10L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(10L)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Job posting 10 is already tracked as an application.");

        verify(jobApplicationRepository, never()).saveAndFlush(any());
    }

    @Test
    void mapsDatabaseUniquenessRaceToConflict() {
        JobPosting jobPosting = jobPosting(10L);
        when(jobPostingRepository.findById(10L)).thenReturn(Optional.of(jobPosting));
        when(jobApplicationRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("constraint details"));

        assertThatThrownBy(() -> service.create(request(10L)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Job posting 10 is already tracked as an application.")
                .hasMessageNotContaining("constraint");
    }

    @Test
    void filtersApplicationsByStatusAndKeepsRepositoryOrdering() {
        JobApplication application = application(
                1L,
                jobPosting(10L),
                null,
                null,
                ApplicationStatus.APPLIED,
                LocalDate.of(2026, 7, 20)
        );
        when(jobApplicationRepository.findAllByStatusOrderByUpdatedAtDesc(
                ApplicationStatus.APPLIED
        )).thenReturn(List.of(application));

        List<JobApplicationResponse> responses = service.findAll(ApplicationStatus.APPLIED);

        assertThat(responses).extracting(JobApplicationResponse::id).containsExactly(1L);
        verify(jobApplicationRepository)
                .findAllByStatusOrderByUpdatedAtDesc(ApplicationStatus.APPLIED);
    }

    @Test
    void updatesOptionalRelationshipsAndFollowUpFieldsWithoutChangingStatus() {
        JobPosting jobPosting = jobPosting(10L);
        Resume resume = resume(20L);
        Analysis analysis = analysis(30L, jobPosting, resume);
        JobApplication application = application(
                1L, jobPosting, null, null, ApplicationStatus.SAVED, null
        );
        when(jobApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(resumeRepository.findById(20L)).thenReturn(Optional.of(resume));
        when(analysisRepository.findById(30L)).thenReturn(Optional.of(analysis));
        when(jobApplicationRepository.saveAndFlush(application)).thenReturn(application);

        JobApplicationResponse response = service.update(1L, new UpdateApplicationRequest(
                20L,
                30L,
                LocalDate.of(2026, 7, 18),
                "Send follow-up",
                OffsetDateTime.parse("2026-07-26T10:30:00+08:00"),
                "Contacted the recruiter"
        ));

        assertThat(response.status()).isEqualTo(ApplicationStatus.SAVED);
        assertThat(response.resume().id()).isEqualTo(20L);
        assertThat(response.analysis().id()).isEqualTo(30L);
        assertThat(response.nextAction()).isEqualTo("Send follow-up");
        assertThat(response.notes()).isEqualTo("Contacted the recruiter");
        verifyNoInteractions(statusHistoryRepository);
    }

    @Test
    void statusTransitionCreatesHistoryAndAutomaticallySetsAppliedDate() {
        JobApplication application = application(
                1L, jobPosting(10L), null, null, ApplicationStatus.SAVED, null
        );
        when(jobApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(jobApplicationRepository.saveAndFlush(application)).thenReturn(application);

        JobApplicationResponse response = service.updateStatus(
                1L,
                new UpdateApplicationStatusRequest(ApplicationStatus.INTERVIEW)
        );

        assertThat(response.status()).isEqualTo(ApplicationStatus.INTERVIEW);
        assertThat(response.appliedAt()).isEqualTo(LocalDate.now(ZoneOffset.UTC));
        ArgumentCaptor<ApplicationStatusHistory> history =
                ArgumentCaptor.forClass(ApplicationStatusHistory.class);
        verify(statusHistoryRepository).saveAndFlush(history.capture());
        assertThat(history.getValue().getPreviousStatus()).isEqualTo(ApplicationStatus.SAVED);
        assertThat(history.getValue().getNewStatus()).isEqualTo(ApplicationStatus.INTERVIEW);
    }

    @Test
    void laterStatusTransitionPreservesManuallySuppliedAppliedDate() {
        LocalDate manualDate = LocalDate.of(2026, 6, 15);
        JobApplication application = application(
                1L,
                jobPosting(10L),
                null,
                null,
                ApplicationStatus.SAVED,
                manualDate
        );
        when(jobApplicationRepository.findById(1L)).thenReturn(Optional.of(application));
        when(jobApplicationRepository.saveAndFlush(application)).thenReturn(application);

        JobApplicationResponse response = service.updateStatus(
                1L,
                new UpdateApplicationStatusRequest(ApplicationStatus.APPLIED)
        );

        assertThat(response.appliedAt()).isEqualTo(manualDate);
    }

    @Test
    void sameStatusDoesNotSaveOrCreateDuplicateHistory() {
        JobApplication application = application(
                1L,
                jobPosting(10L),
                null,
                null,
                ApplicationStatus.SAVED,
                null
        );
        when(jobApplicationRepository.findById(1L)).thenReturn(Optional.of(application));

        JobApplicationResponse response = service.updateStatus(
                1L,
                new UpdateApplicationStatusRequest(ApplicationStatus.SAVED)
        );

        assertThat(response.status()).isEqualTo(ApplicationStatus.SAVED);
        verify(jobApplicationRepository, never()).saveAndFlush(any());
        verifyNoInteractions(statusHistoryRepository);
    }

    @Test
    void returnsHistoryInRepositoryReverseChronologicalOrder() {
        when(jobApplicationRepository.existsById(1L)).thenReturn(true);
        ApplicationStatusHistory latest = history(
                3L,
                ApplicationStatus.APPLIED,
                ApplicationStatus.INTERVIEW,
                Instant.parse("2026-07-22T10:00:00Z")
        );
        ApplicationStatusHistory initial = history(
                1L,
                null,
                ApplicationStatus.SAVED,
                Instant.parse("2026-07-20T10:00:00Z")
        );
        when(statusHistoryRepository.findByJobApplicationIdOrderByChangedAtDescIdDesc(1L))
                .thenReturn(List.of(latest, initial));

        List<ApplicationStatusHistoryResponse> history = service.findHistory(1L);

        assertThat(history).extracting(ApplicationStatusHistoryResponse::id)
                .containsExactly(3L, 1L);
    }

    @Test
    void deletesOnlyTheApplication() {
        JobApplication application = application(
                1L,
                jobPosting(10L),
                resume(20L),
                null,
                ApplicationStatus.SAVED,
                null
        );
        when(jobApplicationRepository.findById(1L)).thenReturn(Optional.of(application));

        service.delete(1L);

        verify(jobApplicationRepository).delete(application);
        verify(jobApplicationRepository).flush();
        verifyNoInteractions(
                jobPostingRepository,
                resumeRepository,
                analysisRepository,
                statusHistoryRepository
        );
    }

    private CreateApplicationRequest request(Long jobPostingId) {
        return new CreateApplicationRequest(
                jobPostingId, null, null, null, null, null, null, null
        );
    }

    private void saveApplicationsWithGeneratedId() {
        when(jobApplicationRepository.saveAndFlush(any(JobApplication.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0), 1L));
        when(statusHistoryRepository.saveAndFlush(any(ApplicationStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private JobPosting jobPosting(Long id) {
        JobPosting jobPosting = mock(JobPosting.class);
        lenient().when(jobPosting.getId()).thenReturn(id);
        lenient().when(jobPosting.getTitle()).thenReturn("Support Engineer");
        lenient().when(jobPosting.getCompany()).thenReturn("Sunbird Support");
        return jobPosting;
    }

    private Resume resume(Long id) {
        Resume resume = mock(Resume.class);
        lenient().when(resume.getId()).thenReturn(id);
        lenient().when(resume.getName()).thenReturn("Support Resume");
        lenient().when(resume.getTargetRole()).thenReturn("IT Support");
        return resume;
    }

    private Analysis analysis(
            Long id,
            JobPosting jobPosting,
            Resume resume
    ) {
        Analysis analysis = mock(Analysis.class);
        lenient().when(analysis.getId()).thenReturn(id);
        lenient().when(analysis.getJobPosting()).thenReturn(jobPosting);
        lenient().when(analysis.getResume()).thenReturn(resume);
        lenient().when(analysis.getMatchScore()).thenReturn(82);
        lenient().when(analysis.getProvider()).thenReturn("fake");
        lenient().when(analysis.getModel()).thenReturn("keyword-matcher-v1");
        return analysis;
    }

    private JobApplication application(
            Long id,
            JobPosting jobPosting,
            Resume resume,
            Analysis analysis,
            ApplicationStatus status,
            LocalDate appliedAt
    ) {
        return persisted(new JobApplication(
                jobPosting,
                resume,
                analysis,
                status,
                appliedAt,
                null,
                null,
                null
        ), id);
    }

    private JobApplication persisted(JobApplication application, Long id) {
        ReflectionTestUtils.setField(application, "id", id);
        ReflectionTestUtils.setField(application, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(application, "updatedAt", UPDATED_AT);
        return application;
    }

    private ApplicationStatusHistory history(
            Long id,
            ApplicationStatus previousStatus,
            ApplicationStatus newStatus,
            Instant changedAt
    ) {
        ApplicationStatusHistory history = new ApplicationStatusHistory(
                mock(JobApplication.class),
                previousStatus,
                newStatus
        );
        ReflectionTestUtils.setField(history, "id", id);
        ReflectionTestUtils.setField(history, "changedAt", changedAt);
        return history;
    }
}
