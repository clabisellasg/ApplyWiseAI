package com.genesis.applywise.analysis;

import com.genesis.applywise.ai.AiAnalysisClient;
import com.genesis.applywise.ai.AnalysisResult;
import com.genesis.applywise.common.exception.ResourceNotFoundException;
import com.genesis.applywise.job.JobPosting;
import com.genesis.applywise.job.JobPostingRepository;
import com.genesis.applywise.resume.Resume;
import com.genesis.applywise.resume.ResumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AnalysisService {

    private final AnalysisRepository analysisRepository;
    private final ResumeRepository resumeRepository;
    private final JobPostingRepository jobPostingRepository;
    private final AiAnalysisClient aiAnalysisClient;

    public AnalysisService(
            AnalysisRepository analysisRepository,
            ResumeRepository resumeRepository,
            JobPostingRepository jobPostingRepository,
            AiAnalysisClient aiAnalysisClient
    ) {
        this.analysisRepository = analysisRepository;
        this.resumeRepository = resumeRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.aiAnalysisClient = aiAnalysisClient;
    }

    @Transactional
    public AnalysisResponse create(CreateAnalysisRequest request) {
        Resume resume = resumeRepository.findById(request.resumeId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found: " + request.resumeId()));
        JobPosting jobPosting = jobPostingRepository.findById(request.jobPostingId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Job posting not found: " + request.jobPostingId()
                ));

        AnalysisResult result = aiAnalysisClient.analyze(
                resume.getContent(),
                jobPosting.getDescription()
        );
        Analysis analysis = new Analysis(
                resume,
                jobPosting,
                result,
                aiAnalysisClient.provider(),
                aiAnalysisClient.model(),
                aiAnalysisClient.promptVersion()
        );

        return toResponse(analysisRepository.saveAndFlush(analysis));
    }

    @Transactional(readOnly = true)
    public List<AnalysisResponse> findAll() {
        return analysisRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AnalysisResponse findById(Long id) {
        return toResponse(analysisRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found: " + id)));
    }

    private AnalysisResponse toResponse(Analysis analysis) {
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
                analysis.getCreatedAt()
        );
    }
}
