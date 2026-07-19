package com.genesis.applywise.job;

import com.genesis.applywise.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class JobPostingService {

    private final JobPostingRepository jobPostingRepository;

    public JobPostingService(JobPostingRepository jobPostingRepository) {
        this.jobPostingRepository = jobPostingRepository;
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
        jobPostingRepository.delete(findJobPosting(id));
    }

    private JobPosting findJobPosting(Long id) {
        return jobPostingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job posting not found: " + id));
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
