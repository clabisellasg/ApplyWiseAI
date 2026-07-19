package com.genesis.applywise.resume;

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
class ResumeServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-10T08:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-01-11T09:30:00Z");

    @Mock
    private ResumeRepository resumeRepository;

    private ResumeService resumeService;

    @BeforeEach
    void setUp() {
        resumeService = new ResumeService(resumeRepository);
    }

    @Test
    void createsResume() {
        CreateResumeRequest request = new CreateResumeRequest(
                "Primary Resume",
                "Software Engineer",
                "Experienced Java developer"
        );
        when(resumeRepository.saveAndFlush(any(Resume.class)))
                .thenAnswer(invocation -> persisted(invocation.getArgument(0), 1L));

        ResumeResponse response = resumeService.create(request);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo(request.name());
        assertThat(response.targetRole()).isEqualTo(request.targetRole());
        assertThat(response.content()).isEqualTo(request.content());
    }

    @Test
    void returnsAllResumes() {
        Resume first = persisted(resume("Engineering Resume"), 1L);
        Resume second = persisted(resume("Support Resume"), 2L);
        when(resumeRepository.findAll()).thenReturn(List.of(first, second));

        List<ResumeResponse> responses = resumeService.findAll();

        assertThat(responses).extracting(ResumeResponse::id).containsExactly(1L, 2L);
    }

    @Test
    void returnsResumeById() {
        Resume resume = persisted(resume("Primary Resume"), 1L);
        when(resumeRepository.findById(1L)).thenReturn(Optional.of(resume));

        ResumeResponse response = resumeService.findById(1L);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Primary Resume");
    }

    @Test
    void throwsWhenResumeDoesNotExist() {
        when(resumeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resumeService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Resume not found: 99");
    }

    @Test
    void updatesResume() {
        Resume resume = persisted(resume("Old Resume"), 1L);
        UpdateResumeRequest request = new UpdateResumeRequest(
                "Data Resume",
                "Data Analyst",
                "Experienced data analyst"
        );
        when(resumeRepository.findById(1L)).thenReturn(Optional.of(resume));
        when(resumeRepository.saveAndFlush(resume)).thenReturn(resume);

        ResumeResponse response = resumeService.update(1L, request);

        assertThat(response.name()).isEqualTo("Data Resume");
        assertThat(response.targetRole()).isEqualTo("Data Analyst");
        assertThat(response.content()).isEqualTo("Experienced data analyst");
    }

    @Test
    void deletesExistingResume() {
        Resume resume = persisted(resume("Primary Resume"), 1L);
        when(resumeRepository.findById(1L)).thenReturn(Optional.of(resume));

        resumeService.delete(1L);

        verify(resumeRepository).delete(resume);
    }

    private Resume resume(String name) {
        return new Resume(name, "Software Engineer", "Experienced Java developer");
    }

    private Resume persisted(Resume resume, Long id) {
        ReflectionTestUtils.setField(resume, "id", id);
        ReflectionTestUtils.setField(resume, "createdAt", CREATED_AT);
        ReflectionTestUtils.setField(resume, "updatedAt", UPDATED_AT);
        return resume;
    }
}
