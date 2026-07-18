package com.genesis.applywise.job;

import com.genesis.applywise.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobPostingServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-10T08:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-01-11T09:30:00Z");

    @Mock
    private JobPostingRepository jobPostingRepository;

    private JobPostingService jobPostingService;

    @BeforeEach
    void setUp() {
        jobPostingService = new JobPostingService(jobPostingRepository);
    }

    @Test
    void createsJobPosting() {
        CreateJobRequest request = new CreateJobRequest(
                "Backend Engineer",
                "Genesis",
                "Build reliable services",
                "https://example.com/jobs/1"
        );
        when(jobPostingRepository.saveAndFlush(any(JobPosting.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0), 1L));

        JobPostingResponse response = jobPostingService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo(request.title());
        assertThat(response.company()).isEqualTo(request.company());
        assertThat(response.description()).isEqualTo(request.description());
        assertThat(response.sourceUrl()).isEqualTo(request.sourceUrl());
    }

    @Test
    void returnsAllJobPostings() {
        JobPosting first = persisted(jobPosting("Engineer"), 1L);
        JobPosting second = persisted(jobPosting("Designer"), 2L);
        when(jobPostingRepository.findAll()).thenReturn(List.of(first, second));

        List<JobPostingResponse> responses = jobPostingService.findAll();

        assertThat(responses).extracting(JobPostingResponse::id).containsExactly(1L, 2L);
    }

    @Test
    void returnsJobPostingById() {
        JobPosting jobPosting = persisted(jobPosting("Engineer"), 1L);
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(jobPosting));

        JobPostingResponse response = jobPostingService.findById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("Engineer");
    }

    @Test
    void throwsWhenJobPostingDoesNotExist() {
        when(jobPostingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> jobPostingService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Job posting not found: 99");
    }

    @Test
    void updatesJobPosting() {
        JobPosting jobPosting = persisted(jobPosting("Old title"), 1L);
        UpdateJobRequest request = new UpdateJobRequest(
                "New title",
                "New company",
                "New description",
                null
        );
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(jobPosting));
        when(jobPostingRepository.saveAndFlush(jobPosting)).thenReturn(jobPosting);

        JobPostingResponse response = jobPostingService.update(1L, request);

        assertThat(response.title()).isEqualTo("New title");
        assertThat(response.company()).isEqualTo("New company");
        assertThat(response.description()).isEqualTo("New description");
        assertThat(response.sourceUrl()).isNull();
    }

    @Test
    void deletesExistingJobPosting() {
        JobPosting jobPosting = persisted(jobPosting("Engineer"), 1L);
        when(jobPostingRepository.findById(1L)).thenReturn(Optional.of(jobPosting));

        jobPostingService.delete(1L);

        verify(jobPostingRepository).delete(jobPosting);
    }

    private JobPosting jobPosting(String title) {
        return new JobPosting(title, "Genesis", "Description", null);
    }

    private JobPosting persisted(JobPosting jobPosting, Long id) {
        ReflectionTestUtils.setField(jobPosting, "id", id);
        ReflectionTestUtils.setField(jobPosting, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(jobPosting, "updatedAt", UPDATED_AT);
        return jobPosting;
    }
}
