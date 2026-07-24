package com.genesis.applywise.application;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    @EntityGraph(attributePaths = {"jobPosting", "resume", "latestAnalysis"})
    List<JobApplication> findAllByOrderByUpdatedAtDesc();

    @EntityGraph(attributePaths = {"jobPosting", "resume", "latestAnalysis"})
    List<JobApplication> findAllByStatusOrderByUpdatedAtDesc(ApplicationStatus status);

    @Override
    @EntityGraph(attributePaths = {"jobPosting", "resume", "latestAnalysis"})
    Optional<JobApplication> findById(Long id);

    boolean existsByJobPostingId(Long jobPostingId);
}
