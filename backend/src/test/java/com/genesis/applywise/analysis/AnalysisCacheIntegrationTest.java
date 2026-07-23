package com.genesis.applywise.analysis;

import com.genesis.applywise.job.CreateJobRequest;
import com.genesis.applywise.job.JobPostingRepository;
import com.genesis.applywise.job.JobPostingResponse;
import com.genesis.applywise.job.JobPostingService;
import com.genesis.applywise.resume.CreateResumeRequest;
import com.genesis.applywise.resume.ResumeRepository;
import com.genesis.applywise.resume.ResumeResponse;
import com.genesis.applywise.resume.ResumeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "ai.provider=fake")
@EnabledIfSystemProperty(named = "db.integration", matches = "true")
class AnalysisCacheIntegrationTest {

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private AnalysisRepository analysisRepository;

    @Autowired
    private ResumeService resumeService;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JobPostingService jobPostingService;

    @Autowired
    private JobPostingRepository jobPostingRepository;

    @Test
    void simultaneousIdenticalRequestsReuseOneStoredAnalysis() throws Exception {
        ResumeResponse resume = resumeService.create(new CreateResumeRequest(
                "Synthetic cache test resume",
                "Software Engineer",
                "Built Java and Spring Boot REST APIs."
        ));
        JobPostingResponse job = jobPostingService.create(new CreateJobRequest(
                "Synthetic cache test role",
                "Fictional Cache Works",
                "Java, Spring Boot, REST APIs, and Docker required.",
                null
        ));
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            CreateAnalysisRequest request = new CreateAnalysisRequest(resume.id(), job.id());
            Future<AnalysisResponse> first = executor.submit(() -> createWhenReleased(request, ready, start));
            Future<AnalysisResponse> second = executor.submit(() -> createWhenReleased(request, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<AnalysisResponse> responses = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));

            assertThat(responses).extracting(AnalysisResponse::id).containsOnly(responses.getFirst().id());
            assertThat(responses).extracting(AnalysisResponse::cacheHit).containsExactlyInAnyOrder(false, true);
            assertThat(analysisRepository.findAllByResumeIdAndJobPostingId(resume.id(), job.id())).hasSize(1);
        } finally {
            executor.shutdownNow();
            analysisRepository.deleteAll(
                    analysisRepository.findAllByResumeIdAndJobPostingId(resume.id(), job.id())
            );
            analysisRepository.flush();
            resumeRepository.deleteById(resume.id());
            jobPostingRepository.deleteById(job.id());
        }
    }

    private AnalysisResponse createWhenReleased(
            CreateAnalysisRequest request,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent cache test did not start in time");
        }
        return analysisService.create(request);
    }
}
