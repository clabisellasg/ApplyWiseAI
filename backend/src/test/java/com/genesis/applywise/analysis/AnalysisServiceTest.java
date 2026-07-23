package com.genesis.applywise.analysis;

import com.genesis.applywise.ai.AiAnalysisClient;
import com.genesis.applywise.ai.AnalysisResult;
import com.genesis.applywise.ai.MatchStatus;
import com.genesis.applywise.ai.NvidiaProviderException;
import com.genesis.applywise.ai.SkillAssessment;
import com.genesis.applywise.common.exception.ResourceNotFoundException;
import com.genesis.applywise.job.JobPosting;
import com.genesis.applywise.job.JobPostingRepository;
import com.genesis.applywise.resume.Resume;
import com.genesis.applywise.resume.ResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-02-01T10:15:30Z");
    private static final String INPUT_HASH = "a".repeat(64);

    @Mock
    private AnalysisRepository analysisRepository;

    @Mock
    private ResumeRepository resumeRepository;

    @Mock
    private JobPostingRepository jobPostingRepository;

    @Mock
    private AiAnalysisClient aiAnalysisClient;

    @Mock
    private AnalysisInputFingerprint inputFingerprint;

    @Mock
    private AnalysisInputLockRepository inputLockRepository;

    @Mock
    private AnalysisPersistenceWriter persistenceWriter;

    private AnalysisService analysisService;

    @BeforeEach
    void setUp() {
        analysisService = new AnalysisService(
                analysisRepository,
                resumeRepository,
                jobPostingRepository,
                aiAnalysisClient,
                inputFingerprint,
                inputLockRepository,
                persistenceWriter
        );
    }

    @Test
    void loadsSourcesCallsAiClientAndSavesCompleteAnalysis() {
        Resume resume = resume(4L, "Java and Spring Boot experience");
        JobPosting jobPosting = jobPosting(9L, "Java, Spring Boot, and Docker required");
        AnalysisResult result = result();
        when(resumeRepository.findById(4L)).thenReturn(Optional.of(resume));
        when(jobPostingRepository.findById(9L)).thenReturn(Optional.of(jobPosting));
        when(aiAnalysisClient.analyze(resume.getContent(), jobPosting.getDescription())).thenReturn(result);
        when(aiAnalysisClient.provider()).thenReturn("fake");
        when(aiAnalysisClient.model()).thenReturn("keyword-matcher-v1");
        when(aiAnalysisClient.promptVersion()).thenReturn("v1");
        when(inputFingerprint.generate(
                resume.getContent(),
                jobPosting.getDescription(),
                "fake",
                "keyword-matcher-v1",
                "v1"
        )).thenReturn(INPUT_HASH);
        when(analysisRepository.findByInputHashAndProviderAndModelAndPromptVersion(
                INPUT_HASH,
                "fake",
                "keyword-matcher-v1",
                "v1"
        )).thenReturn(Optional.empty());
        when(persistenceWriter.save(org.mockito.ArgumentMatchers.any(Analysis.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0), 1L));

        AnalysisResponse response = analysisService.create(new CreateAnalysisRequest(4L, 9L));

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.resumeId()).isEqualTo(4L);
        assertThat(response.jobPostingId()).isEqualTo(9L);
        assertThat(response.result()).isEqualTo(result);
        assertThat(response.provider()).isEqualTo("fake");
        assertThat(response.model()).isEqualTo("keyword-matcher-v1");
        assertThat(response.promptVersion()).isEqualTo("v1");
        assertThat(response.cacheHit()).isFalse();
        verify(aiAnalysisClient).analyze(resume.getContent(), jobPosting.getDescription());

        ArgumentCaptor<Analysis> captor = ArgumentCaptor.forClass(Analysis.class);
        verify(persistenceWriter).save(captor.capture());
        assertThat(captor.getValue().getMatchScore()).isEqualTo(result.matchScore());
        assertThat(captor.getValue().getSummary()).isEqualTo(result.summary());
        assertThat(captor.getValue().getResult()).isEqualTo(result);
        assertThat(captor.getValue().getInputHash()).isEqualTo(INPUT_HASH);

        InOrder order = inOrder(inputLockRepository, analysisRepository, aiAnalysisClient, persistenceWriter);
        order.verify(inputLockRepository).acquire(INPUT_HASH);
        order.verify(analysisRepository).findByInputHashAndProviderAndModelAndPromptVersion(
                INPUT_HASH,
                "fake",
                "keyword-matcher-v1",
                "v1"
        );
        order.verify(aiAnalysisClient).analyze(resume.getContent(), jobPosting.getDescription());
        order.verify(persistenceWriter).save(org.mockito.ArgumentMatchers.any(Analysis.class));
    }

    @Test
    void cacheHitReturnsExistingAnalysisWithoutCallingAiClient() {
        Resume resume = sourceResume("Java experience");
        JobPosting jobPosting = sourceJobPosting("Java required");
        Analysis existing = persisted(analysis(4L, 9L), 12L);
        when(resumeRepository.findById(4L)).thenReturn(Optional.of(resume));
        when(jobPostingRepository.findById(9L)).thenReturn(Optional.of(jobPosting));
        stubCacheKey(resume, jobPosting);
        when(analysisRepository.findByInputHashAndProviderAndModelAndPromptVersion(
                INPUT_HASH,
                "fake",
                "keyword-matcher-v1",
                "v1"
        )).thenReturn(Optional.of(existing));

        AnalysisResponse response = analysisService.create(new CreateAnalysisRequest(4L, 9L));

        assertThat(response.id()).isEqualTo(12L);
        assertThat(response.cacheHit()).isTrue();
        verify(aiAnalysisClient, never()).analyze(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
        verifyNoInteractions(persistenceWriter);
    }

    @Test
    void throwsWhenResumeDoesNotExist() {
        when(resumeRepository.findById(44L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analysisService.create(new CreateAnalysisRequest(44L, 9L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Resume not found: 44");

        verifyNoInteractions(
                jobPostingRepository,
                aiAnalysisClient,
                analysisRepository,
                inputFingerprint,
                inputLockRepository,
                persistenceWriter
        );
    }

    @Test
    void throwsWhenJobPostingDoesNotExist() {
        when(resumeRepository.findById(4L)).thenReturn(Optional.of(mock(Resume.class)));
        when(jobPostingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analysisService.create(new CreateAnalysisRequest(4L, 99L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Job posting not found: 99");

        verify(aiAnalysisClient, never()).analyze(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
        verifyNoInteractions(analysisRepository);
    }

    @Test
    void doesNotSaveOrFallBackWhenConfiguredProviderFails() {
        Resume resume = mock(Resume.class);
        JobPosting jobPosting = mock(JobPosting.class);
        when(resume.getContent()).thenReturn("Java experience");
        when(jobPosting.getDescription()).thenReturn("Java required");
        NvidiaProviderException failure = new NvidiaProviderException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "NVIDIA analysis service is temporarily unavailable."
        );
        when(resumeRepository.findById(4L)).thenReturn(Optional.of(resume));
        when(jobPostingRepository.findById(9L)).thenReturn(Optional.of(jobPosting));
        stubCacheKey(resume, jobPosting);
        when(analysisRepository.findByInputHashAndProviderAndModelAndPromptVersion(
                INPUT_HASH,
                "fake",
                "keyword-matcher-v1",
                "v1"
        )).thenReturn(Optional.empty());
        when(aiAnalysisClient.analyze(resume.getContent(), jobPosting.getDescription())).thenThrow(failure);

        assertThatThrownBy(() -> analysisService.create(new CreateAnalysisRequest(4L, 9L)))
                .isSameAs(failure);

        verifyNoInteractions(persistenceWriter);
    }

    @Test
    void uniqueConflictReturnsTheAnalysisCommittedByConcurrentRequest() {
        Resume resume = sourceResume("Java experience");
        JobPosting jobPosting = sourceJobPosting("Java required");
        Analysis existing = persisted(analysis(4L, 9L), 15L);
        when(resumeRepository.findById(4L)).thenReturn(Optional.of(resume));
        when(jobPostingRepository.findById(9L)).thenReturn(Optional.of(jobPosting));
        stubCacheKey(resume, jobPosting);
        when(analysisRepository.findByInputHashAndProviderAndModelAndPromptVersion(
                INPUT_HASH,
                "fake",
                "keyword-matcher-v1",
                "v1"
        )).thenReturn(Optional.empty()).thenReturn(Optional.of(existing));
        when(aiAnalysisClient.analyze(resume.getContent(), jobPosting.getDescription())).thenReturn(result());
        when(persistenceWriter.save(org.mockito.ArgumentMatchers.any(Analysis.class)))
                .thenThrow(new DataIntegrityViolationException("simulated unique conflict"));

        AnalysisResponse response = analysisService.create(new CreateAnalysisRequest(4L, 9L));

        assertThat(response.id()).isEqualTo(15L);
        assertThat(response.cacheHit()).isTrue();
        verify(aiAnalysisClient).analyze(resume.getContent(), jobPosting.getDescription());
    }

    @Test
    void returnsAllAnalyses() {
        Analysis first = persisted(analysis(1L, 2L), 10L);
        Analysis second = persisted(analysis(3L, 4L), 11L);
        when(analysisRepository.findAll()).thenReturn(List.of(first, second));

        List<AnalysisResponse> responses = analysisService.findAll();

        assertThat(responses).extracting(AnalysisResponse::id).containsExactly(10L, 11L);
    }

    @Test
    void returnsAnalysisById() {
        Analysis analysis = persisted(analysis(1L, 2L), 10L);
        when(analysisRepository.findById(10L)).thenReturn(Optional.of(analysis));

        AnalysisResponse response = analysisService.findById(10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.matchScore()).isEqualTo(50);
    }

    @Test
    void throwsWhenAnalysisDoesNotExist() {
        when(analysisRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analysisService.findById(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Analysis not found: 404");
    }

    private Resume resume(Long id, String content) {
        Resume resume = mock(Resume.class);
        when(resume.getId()).thenReturn(id);
        when(resume.getContent()).thenReturn(content);
        return resume;
    }

    private JobPosting jobPosting(Long id, String description) {
        JobPosting jobPosting = mock(JobPosting.class);
        when(jobPosting.getId()).thenReturn(id);
        when(jobPosting.getDescription()).thenReturn(description);
        return jobPosting;
    }

    private Resume sourceResume(String content) {
        Resume resume = mock(Resume.class);
        when(resume.getContent()).thenReturn(content);
        return resume;
    }

    private JobPosting sourceJobPosting(String description) {
        JobPosting jobPosting = mock(JobPosting.class);
        when(jobPosting.getDescription()).thenReturn(description);
        return jobPosting;
    }

    private Analysis analysis(Long resumeId, Long jobPostingId) {
        Resume resume = mock(Resume.class);
        JobPosting jobPosting = mock(JobPosting.class);
        when(resume.getId()).thenReturn(resumeId);
        when(jobPosting.getId()).thenReturn(jobPostingId);

        return new Analysis(
                resume,
                jobPosting,
                result(),
                "fake",
                "keyword-matcher-v1",
                "v1",
                INPUT_HASH
        );
    }

    private Analysis persisted(Analysis analysis, Long id) {
        ReflectionTestUtils.setField(analysis, "id", id);
        ReflectionTestUtils.setField(analysis, "createdAt", CREATED_AT);
        return analysis;
    }

    private AnalysisResult result() {
        return new AnalysisResult(
                50,
                "One matched skill and one missing skill.",
                List.of(
                        new SkillAssessment("Java", MatchStatus.MATCHED, "Java experience", "Matched."),
                        new SkillAssessment("Docker", MatchStatus.MISSING, null, "Missing.")
                ),
                List.of("Java is supported by resume evidence."),
                List.of("Docker is requested but is not evidenced in the resume."),
                List.of("Develop or document Docker experience before claiming it in an application.")
        );
    }

    private void stubCacheKey(Resume resume, JobPosting jobPosting) {
        when(aiAnalysisClient.provider()).thenReturn("fake");
        when(aiAnalysisClient.model()).thenReturn("keyword-matcher-v1");
        when(aiAnalysisClient.promptVersion()).thenReturn("v1");
        when(inputFingerprint.generate(
                resume.getContent(),
                jobPosting.getDescription(),
                "fake",
                "keyword-matcher-v1",
                "v1"
        )).thenReturn(INPUT_HASH);
    }
}
