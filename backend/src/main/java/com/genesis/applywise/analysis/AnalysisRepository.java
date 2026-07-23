package com.genesis.applywise.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {

    Optional<Analysis> findByInputHashAndProviderAndModelAndPromptVersion(
            String inputHash,
            String provider,
            String model,
            String promptVersion
    );

    List<Analysis> findAllByResumeIdAndJobPostingId(Long resumeId, Long jobPostingId);
}
