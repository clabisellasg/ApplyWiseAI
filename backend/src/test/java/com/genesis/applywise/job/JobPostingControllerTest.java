package com.genesis.applywise.job;

import com.genesis.applywise.common.exception.GlobalExceptionHandler;
import com.genesis.applywise.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobPostingController.class)
@Import(GlobalExceptionHandler.class)
class JobPostingControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-10T08:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-01-11T09:30:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobPostingService jobPostingService;

    @Test
    void createsJobPosting() throws Exception {
        when(jobPostingService.create(any(CreateJobRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Backend Engineer",
                                  "company": "Genesis",
                                  "description": "Build reliable services",
                                  "sourceUrl": "https://example.com/jobs/1"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/jobs/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Backend Engineer"));
    }

    @Test
    void rejectsInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/api/jobs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": " ",
                                  "company": "Genesis",
                                  "description": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(jobPostingService);
    }

    @Test
    void returnsAllJobPostings() throws Exception {
        when(jobPostingService.findAll()).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].company").value("Genesis"));
    }

    @Test
    void returnsJobPostingById() throws Exception {
        when(jobPostingService.findById(1L)).thenReturn(response());

        mockMvc.perform(get("/api/jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Build reliable services"))
                .andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()));
    }

    @Test
    void returnsNotFoundWhenJobPostingDoesNotExist() throws Exception {
        when(jobPostingService.findById(99L))
                .thenThrow(new ResourceNotFoundException("Job posting not found: 99"));

        mockMvc.perform(get("/api/jobs/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Job posting not found: 99"));
    }

    @Test
    void updatesJobPosting() throws Exception {
        when(jobPostingService.update(any(Long.class), any(UpdateJobRequest.class))).thenReturn(response());

        mockMvc.perform(put("/api/jobs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Backend Engineer",
                                  "company": "Genesis",
                                  "description": "Build reliable services",
                                  "sourceUrl": "https://example.com/jobs/1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.updatedAt").value(UPDATED_AT.toString()));
    }

    @Test
    void deletesJobPosting() throws Exception {
        mockMvc.perform(delete("/api/jobs/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    private JobPostingResponse response() {
        return new JobPostingResponse(
                1L,
                "Backend Engineer",
                "Genesis",
                "Build reliable services",
                "https://example.com/jobs/1",
                CREATED_AT,
                UPDATED_AT
        );
    }
}
