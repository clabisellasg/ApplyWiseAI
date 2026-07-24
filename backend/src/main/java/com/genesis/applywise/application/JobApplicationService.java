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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final ApplicationStatusHistoryRepository statusHistoryRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ResumeRepository resumeRepository;
    private final AnalysisRepository analysisRepository;

    public JobApplicationService(
            JobApplicationRepository jobApplicationRepository,
            ApplicationStatusHistoryRepository statusHistoryRepository,
            JobPostingRepository jobPostingRepository,
            ResumeRepository resumeRepository,
            AnalysisRepository analysisRepository
    ) {
        this.jobApplicationRepository = jobApplicationRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.jobPostingRepository = jobPostingRepository;
        this.resumeRepository = resumeRepository;
        this.analysisRepository = analysisRepository;
    }

    @Transactional
    public JobApplicationResponse create(CreateApplicationRequest request) {
        JobPosting jobPosting = findJobPosting(request.jobPostingId());
        rejectDuplicateJob(request.jobPostingId());
        Resume resume = findResume(request.resumeId());
        Analysis analysis = findAnalysis(request.analysisId());
        validateRelationships(jobPosting, resume, analysis);

        ApplicationStatus status = request.status() == null
                ? ApplicationStatus.SAVED
                : request.status();
        LocalDate appliedAt = request.appliedAt();
        if (appliedAt == null && indicatesSubmittedApplication(status)) {
            appliedAt = currentDate();
        }

        JobApplication application = new JobApplication(
                jobPosting,
                resume,
                analysis,
                status,
                appliedAt,
                request.nextAction(),
                request.nextActionAt(),
                request.notes()
        );

        JobApplication saved;
        try {
            saved = jobApplicationRepository.saveAndFlush(application);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateJob(request.jobPostingId());
        }
        statusHistoryRepository.saveAndFlush(
                new ApplicationStatusHistory(saved, null, status)
        );
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<JobApplicationResponse> findAll(ApplicationStatus status) {
        List<JobApplication> applications = status == null
                ? jobApplicationRepository.findAllByOrderByUpdatedAtDesc()
                : jobApplicationRepository.findAllByStatusOrderByUpdatedAtDesc(status);
        return applications.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public JobApplicationResponse findById(Long id) {
        return toResponse(findApplication(id));
    }

    @Transactional
    public JobApplicationResponse update(Long id, UpdateApplicationRequest request) {
        JobApplication application = findApplication(id);
        Resume resume = findResume(request.resumeId());
        Analysis analysis = findAnalysis(request.analysisId());
        validateRelationships(application.getJobPosting(), resume, analysis);

        application.updateDetails(
                resume,
                analysis,
                request.appliedAt(),
                request.nextAction(),
                request.nextActionAt(),
                request.notes()
        );
        return toResponse(jobApplicationRepository.saveAndFlush(application));
    }

    @Transactional
    public JobApplicationResponse updateStatus(
            Long id,
            UpdateApplicationStatusRequest request
    ) {
        JobApplication application = findApplication(id);
        ApplicationStatus previousStatus = application.getStatus();
        ApplicationStatus newStatus = request.status();
        if (previousStatus == newStatus) {
            return toResponse(application);
        }

        application.changeStatus(newStatus);
        if (application.getAppliedAt() == null && indicatesSubmittedApplication(newStatus)) {
            application.setAppliedAt(currentDate());
        }
        JobApplication saved = jobApplicationRepository.saveAndFlush(application);
        statusHistoryRepository.saveAndFlush(
                new ApplicationStatusHistory(saved, previousStatus, newStatus)
        );
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ApplicationStatusHistoryResponse> findHistory(Long id) {
        if (!jobApplicationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Application not found: " + id);
        }
        return statusHistoryRepository
                .findByJobApplicationIdOrderByChangedAtDescIdDesc(id)
                .stream()
                .map(this::toHistoryResponse)
                .toList();
    }

    @Transactional
    public void delete(Long id) {
        jobApplicationRepository.delete(findApplication(id));
        jobApplicationRepository.flush();
    }

    private void rejectDuplicateJob(Long jobPostingId) {
        if (jobApplicationRepository.existsByJobPostingId(jobPostingId)) {
            throw duplicateJob(jobPostingId);
        }
    }

    private ConflictException duplicateJob(Long jobPostingId) {
        return new ConflictException(
                "Job posting " + jobPostingId + " is already tracked as an application."
        );
    }

    private JobApplication findApplication(Long id) {
        return jobApplicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));
    }

    private JobPosting findJobPosting(Long id) {
        return jobPostingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job posting not found: " + id));
    }

    private Resume findResume(Long id) {
        if (id == null) {
            return null;
        }
        return resumeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found: " + id));
    }

    private Analysis findAnalysis(Long id) {
        if (id == null) {
            return null;
        }
        return analysisRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found: " + id));
    }

    private void validateRelationships(
            JobPosting jobPosting,
            Resume resume,
            Analysis analysis
    ) {
        if (analysis == null) {
            return;
        }
        if (!analysis.getJobPosting().getId().equals(jobPosting.getId())) {
            throw new InvalidRequestException(
                    "Analysis " + analysis.getId()
                            + " does not belong to job posting " + jobPosting.getId() + "."
            );
        }
        if (resume != null && !analysis.getResume().getId().equals(resume.getId())) {
            throw new InvalidRequestException(
                    "Analysis " + analysis.getId()
                            + " does not belong to resume " + resume.getId() + "."
            );
        }
    }

    private boolean indicatesSubmittedApplication(ApplicationStatus status) {
        return status == ApplicationStatus.APPLIED
                || status == ApplicationStatus.INTERVIEW
                || status == ApplicationStatus.OFFER;
    }

    private LocalDate currentDate() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private JobApplicationResponse toResponse(JobApplication application) {
        JobPosting jobPosting = application.getJobPosting();
        Resume resume = application.getResume();
        Analysis analysis = application.getLatestAnalysis();
        return new JobApplicationResponse(
                application.getId(),
                new JobSummaryResponse(
                        jobPosting.getId(),
                        jobPosting.getTitle(),
                        jobPosting.getCompany()
                ),
                resume == null ? null : new ResumeSummaryResponse(
                        resume.getId(),
                        resume.getName(),
                        resume.getTargetRole()
                ),
                analysis == null ? null : new AnalysisSummaryResponse(
                        analysis.getId(),
                        analysis.getMatchScore(),
                        analysis.getProvider(),
                        analysis.getModel()
                ),
                application.getStatus(),
                application.getAppliedAt(),
                application.getNextAction(),
                application.getNextActionAt(),
                application.getNotes(),
                application.getCreatedAt(),
                application.getUpdatedAt()
        );
    }

    private ApplicationStatusHistoryResponse toHistoryResponse(
            ApplicationStatusHistory history
    ) {
        return new ApplicationStatusHistoryResponse(
                history.getId(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                history.getChangedAt()
        );
    }
}
