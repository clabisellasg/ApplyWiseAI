package com.genesis.applywise.resume;

import com.genesis.applywise.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ResumeService {

    private final ResumeRepository resumeRepository;

    public ResumeService(ResumeRepository resumeRepository) {
        this.resumeRepository = resumeRepository;
    }

    @Transactional
    public ResumeResponse create(CreateResumeRequest request) {
        Resume resume = new Resume(
                request.name(),
                request.targetRole(),
                request.content()
        );

        return toResponse(resumeRepository.saveAndFlush(resume));
    }

    @Transactional(readOnly = true)
    public List<ResumeResponse> findAll() {
        return resumeRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResumeResponse findById(Long id) {
        return toResponse(findResume(id));
    }

    @Transactional
    public ResumeResponse update(Long id, UpdateResumeRequest request) {
        Resume resume = findResume(id);
        resume.update(
                request.name(),
                request.targetRole(),
                request.content()
        );

        return toResponse(resumeRepository.saveAndFlush(resume));
    }

    @Transactional
    public void delete(Long id) {
        resumeRepository.delete(findResume(id));
    }

    private Resume findResume(Long id) {
        return resumeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found: " + id));
    }

    private ResumeResponse toResponse(Resume resume) {
        return new ResumeResponse(
                resume.getId(),
                resume.getName(),
                resume.getTargetRole(),
                resume.getContent(),
                resume.getCreatedAt(),
                resume.getUpdatedAt()
        );
    }
}
