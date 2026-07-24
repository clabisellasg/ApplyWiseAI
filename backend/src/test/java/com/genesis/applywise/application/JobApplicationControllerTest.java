package com.genesis.applywise.application;

import com.genesis.applywise.common.exception.ConflictException;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobApplicationController.class)
@Import(GlobalExceptionHandler.class)
class JobApplicationControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-07-20T08:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-07-21T09:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobApplicationService jobApplicationService;

    @Test
    void createsApplication() throws Exception {
        when(jobApplicationService.create(any(CreateApplicationRequest.class)))
                .thenReturn(response());

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobPostingId": 10,
                                  "resumeId": 20,
                                  "analysisId": 30,
                                  "status": "APPLIED",
                                  "appliedAt": "2026-07-19",
                                  "nextAction": "Send follow-up",
                                  "nextActionAt": "2026-07-25T09:30:00+08:00",
                                  "notes": "Application submitted"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/applications/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.job.id").value(10))
                .andExpect(jsonPath("$.job.title").value("Support Engineer"))
                .andExpect(jsonPath("$.resume.name").value("Support Resume"))
                .andExpect(jsonPath("$.analysis.score").value(82))
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.nextAction").value("Send follow-up"));
    }

    @Test
    void validatesRequiredJob() throws Exception {
        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isNotEmpty());

        verifyNoInteractions(jobApplicationService);
    }

    @Test
    void validatesNextActionAndNotesMaximumLengths() throws Exception {
        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobPostingId": 10,
                                  "nextAction": "%s"
                                }
                                """.formatted("a".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Invalid value for 'nextAction': size must be between 0 and 500."
                ));

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobPostingId": 10,
                                  "notes": "%s"
                                }
                                """.formatted("n".repeat(10001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "Invalid value for 'notes': size must be between 0 and 10000."
                ));

        verifyNoInteractions(jobApplicationService);
    }

    @Test
    void rejectsMalformedNextActionDateSafely() throws Exception {
        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobPostingId": 10,
                                  "nextActionAt": "tomorrow morning"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed or invalid request body."));

        verifyNoInteractions(jobApplicationService);
    }

    @Test
    void returnsApplicationsFilteredByStatus() throws Exception {
        when(jobApplicationService.findAll(ApplicationStatus.APPLIED))
                .thenReturn(List.of(response()));

        mockMvc.perform(get("/api/applications").queryParam("status", "APPLIED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("APPLIED"));

        verify(jobApplicationService).findAll(ApplicationStatus.APPLIED);
    }

    @Test
    void rejectsUnsupportedStatusQuerySafely() throws Exception {
        mockMvc.perform(get("/api/applications").queryParam("status", "PENDING"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Unsupported value for 'status'."));

        verifyNoInteractions(jobApplicationService);
    }

    @Test
    void returnsApplicationById() throws Exception {
        when(jobApplicationService.findById(1L)).thenReturn(response());

        mockMvc.perform(get("/api/applications/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.updatedAt").value(UPDATED_AT.toString()));
    }

    @Test
    void updatesApplicationDetails() throws Exception {
        when(jobApplicationService.update(eq(1L), any(UpdateApplicationRequest.class)))
                .thenReturn(response());

        mockMvc.perform(put("/api/applications/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resumeId": 20,
                                  "analysisId": 30,
                                  "appliedAt": "2026-07-19",
                                  "nextAction": "Send follow-up",
                                  "nextActionAt": "2026-07-25T09:30:00+08:00",
                                  "notes": "Application submitted"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"));
    }

    @Test
    void updatesStatusThroughDedicatedEndpoint() throws Exception {
        when(jobApplicationService.updateStatus(
                eq(1L),
                any(UpdateApplicationStatusRequest.class)
        )).thenReturn(response());

        mockMvc.perform(patch("/api/applications/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "INTERVIEW"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void validatesStatusPatch() throws Exception {
        mockMvc.perform(patch("/api/applications/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(jobApplicationService);
    }

    @Test
    void returnsStatusHistory() throws Exception {
        when(jobApplicationService.findHistory(1L)).thenReturn(List.of(
                new ApplicationStatusHistoryResponse(
                        2L,
                        ApplicationStatus.SAVED,
                        ApplicationStatus.APPLIED,
                        UPDATED_AT
                ),
                new ApplicationStatusHistoryResponse(
                        1L,
                        null,
                        ApplicationStatus.SAVED,
                        CREATED_AT
                )
        ));

        mockMvc.perform(get("/api/applications/1/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].previousStatus").value("SAVED"))
                .andExpect(jsonPath("$[0].newStatus").value("APPLIED"))
                .andExpect(jsonPath("$[1].previousStatus").doesNotExist());
    }

    @Test
    void deletesApplication() throws Exception {
        mockMvc.perform(delete("/api/applications/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(jobApplicationService).delete(1L);
    }

    @Test
    void returnsConflictForDuplicateTrackedJob() throws Exception {
        when(jobApplicationService.create(any(CreateApplicationRequest.class)))
                .thenThrow(new ConflictException(
                        "Job posting 10 is already tracked as an application."
                ));

        mockMvc.perform(post("/api/applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jobPostingId": 10
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Job posting 10 is already tracked as an application."));
    }

    @Test
    void returnsNotFoundWhenApplicationDoesNotExist() throws Exception {
        when(jobApplicationService.findById(404L))
                .thenThrow(new ResourceNotFoundException("Application not found: 404"));

        mockMvc.perform(get("/api/applications/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Application not found: 404"));
    }

    private JobApplicationResponse response() {
        return new JobApplicationResponse(
                1L,
                new JobSummaryResponse(10L, "Support Engineer", "Sunbird Support"),
                new ResumeSummaryResponse(20L, "Support Resume", "IT Support"),
                new AnalysisSummaryResponse(
                        30L,
                        82,
                        "fake",
                        "keyword-matcher-v1"
                ),
                ApplicationStatus.APPLIED,
                LocalDate.of(2026, 7, 19),
                "Send follow-up",
                OffsetDateTime.parse("2026-07-25T09:30:00+08:00"),
                "Application submitted",
                CREATED_AT,
                UPDATED_AT
        );
    }
}
