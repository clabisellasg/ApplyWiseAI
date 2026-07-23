package com.genesis.applywise.analysis;

import com.genesis.applywise.ai.AiAnalysisClient;
import com.genesis.applywise.ai.AnalysisResult;
import com.genesis.applywise.common.exception.ResourceNotFoundException;
import com.genesis.applywise.job.JobPosting;
import com.genesis.applywise.job.JobPostingRepository;
import com.genesis.applywise.resume.Resume;
import com.genesis.applywise.resume.ResumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnalysisService {

    private final AnalysisRepository analysisRepository;
    private final ResumeRepository resumeRepository;
    private final JobPostingRepository jobPostingRepository;
    private final AiAnalysisClient aiAnalysisClient;
    private final AnalysisInputFingerprint inputFingerprint;
    private final AnalysisInputLockRepository inputLockRepository;
    private final AnalysisPersistenceWriter persistenceWriter;

    public AnalysisService(
            AnalysisRepository analysisRepository,
            ResumeRepository resumeRepository,
            JobPostingRepository jobPostingRepository,
            AiAnalysisClient aiAnalysisClient,
            AnalysisInputFingerprint inputFingerprint,
            AnalysisInputLockRepository inputLockRepository,
            AnalysisPersistenceWriter persistenceWriter
    ) {
        this.analysisRepository = analysisRepository;
        this.resumeRepository = resumeRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.aiAnalysisClient = aiAnalysisClient;
        this.inputFingerprint = inputFingerprint;
        this.inputLockRepository = inputLockRepository;
        this.persistenceWriter = persistenceWriter;
    }

    @Transactional
    public AnalysisResponse create(CreateAnalysisRequest request) {
        Resume resume = resumeRepository.findById(request.resumeId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found: " + request.resumeId()));
        JobPosting jobPosting = jobPostingRepository.findById(request.jobPostingId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job posting not found: " + request.jobPostingId()
                ));

        String provider = aiAnalysisClient.provider();
        String model = aiAnalysisClient.model();
        String promptVersion = aiAnalysisClient.promptVersion();
        String inputHash = inputFingerprint.generate(
                resume.getContent(),
                jobPosting.getDescription(),
                provider,
                model,
                promptVersion
        );

        inputLockRepository.acquire(inputHash);
        Analysis cached = findCached(inputHash, provider, model, promptVersion).orElse(null);
        if (cached != null) {
            return toResponse(cached, true);
        }

        AnalysisResult result = aiAnalysisClient.analyze(
                resume.getContent(),
                jobPosting.getDescription()
        );
        Analysis analysis = new Analysis(
                resume,
                jobPosting,
                result,
                provider,
                model,
                promptVersion,
                inputHash
        );

        try {
            return toResponse(persistenceWriter.save(analysis), false);
        } catch (DataIntegrityViolationException exception) {
            return findCached(inputHash, provider, model, promptVersion)
                    .map(existing -> toResponse(existing, true))
                    .orElseThrow(() -> exception);
        }
    }

    @Transactional(readOnly = true)
    public List<AnalysisResponse> findAll() {
        return analysisRepository.findAll().stream()
                .map(analysis -> toResponse(analysis, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public AnalysisResponse findById(Long id) {
        return toResponse(
                analysisRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Analysis not found: " + id)),
                false
        );
    }

    private java.util.Optional<Analysis> findCached(
            String inputHash,
            String provider,
            String model,
            String promptVersion
    ) {
        return analysisRepository.findByInputHashAndProviderAndModelAndPromptVersion(
                inputHash,
                provider,
                model,
                promptVersion
        );
    }

    private AnalysisResponse toResponse(Analysis analysis, boolean cacheHit) {
        return new AnalysisResponse(
                analysis.getId(),
                analysis.getResume().getId(),
                analysis.getJobPosting().getId(),
                analysis.getMatchScore(),
                analysis.getSummary(),
                analysis.getResult(),
                analysis.getProvider(),
                analysis.getModel(),
                analysis.getPromptVersion(),
                analysis.getCreatedAt(),
                cacheHit
        );
    }
}
