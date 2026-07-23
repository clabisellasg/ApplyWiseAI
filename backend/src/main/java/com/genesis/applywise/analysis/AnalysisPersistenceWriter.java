package com.genesis.applywise.analysis;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AnalysisPersistenceWriter {

    private final AnalysisRepository analysisRepository;

    public AnalysisPersistenceWriter(AnalysisRepository analysisRepository) {
        this.analysisRepository = analysisRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Analysis save(Analysis analysis) {
        return analysisRepository.saveAndFlush(analysis);
    }
}
