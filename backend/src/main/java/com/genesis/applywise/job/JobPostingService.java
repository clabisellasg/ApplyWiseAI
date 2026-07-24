package com.genesis.applywise.job;

import com.genesis.applywise.application.JobApplicationRepository;
import com.genesis.applywise.common.exception.ConflictException;
import com.genesis.applywise.common.exception.ResourceNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;
    private final JobApplicationRepository jobApplicationRepository;

    public JobPostingService(
            JobPostingRepository jobPostingRepository,
            JobApplicationRepository jobApplicationRepository
    ) {
        this.jobPostingRepository = jobPostingRepository;
        this.jobApplicationRepository = jobApplicationRepository;
    }

    @Transactional
    public JobPostingResponse create(CreateJobRequest request) {
        JobPosting jobPosting = new JobPosting(
                request.title(),
                request.company(),
                request.description(),
                request.sourceUrl()
        );

        return toResponse(jobPostingRepository.saveAndFlush(jobPosting));
    }

    @Transactional(readOnly = true)
    public List<JobPostingResponse> findAll() {
        return jobPostingRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public JobPostingResponse findById(Long id) {
        return toResponse(findJobPosting(id));
    }

    @Transactional
    public JobPostingResponse update(Long id, UpdateJobRequest request) {
        JobPosting jobPosting = findJobPosting(id);
        jobPosting.update(
                request.title(),
                request.company(),
                request.description(),
                request.sourceUrl()
        );

        return toResponse(jobPostingRepository.saveAndFlush(jobPosting));
    }

    @Transactional
    public void delete(Long id) {
        JobPosting jobPosting = findJobPosting(id);
        if (jobApplicationRepository.existsByJobPostingId(id)) {
            throw trackedJobConflict(id);
        }
        try {
            jobPostingRepository.delete(jobPosting);
            jobPostingRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException(
                    "Job posting " + id + " cannot be deleted because it is still referenced."
            );
        }
    }

    private JobPosting findJobPosting(Long id) {
        return jobPostingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job posting not found: " + id));
    }

    private ConflictException trackedJobConflict(Long id) {
        return new ConflictException(
                "Job posting " + id + " cannot be deleted while it has a tracked application."
        );
    }

    private JobPostingResponse toResponse(JobPosting jobPosting) {
        return new JobPostingResponse(
                jobPosting.getId(),
                jobPosting.getTitle(),
                jobPosting.getCompany(),
                jobPosting.getDescription(),
                jobPosting.getSourceUrl(),
                jobPosting.getCreatedAt(),
                jobPosting.getUpdatedAt()
        );
    }
}
