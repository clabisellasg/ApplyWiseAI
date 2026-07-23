package com.genesis.applywise.analysis;

import com.genesis.applywise.ai.AnalysisResult;
import com.genesis.applywise.ai.AnalysisValidationFailure;
import com.genesis.applywise.ai.MatchStatus;
import com.genesis.applywise.ai.NvidiaProviderException;
import com.genesis.applywise.ai.SkillAssessment;
import com.genesis.applywise.common.exception.GlobalExceptionHandler;
import com.genesis.applywise.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisController.class)
@Import(GlobalExceptionHandler.class)
class AnalysisControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-02-01T10:15:30Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisService analysisService;

    @Test
    void createsAnalysis() throws Exception {
        when(analysisService.create(any(CreateAnalysisRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resumeId": 4,
                                  "jobPostingId": 9
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/analyses/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.resumeId").value(4))
                .andExpect(jsonPath("$.jobPostingId").value(9))
                .andExpect(jsonPath("$.matchScore").value(50))
                .andExpect(jsonPath("$.result.skills[0].name").value("Java"))
                .andExpect(jsonPath("$.result.skills[0].status").value("MATCHED"))
                .andExpect(jsonPath("$.provider").value("fake"))
                .andExpect(jsonPath("$.model").value("keyword-matcher-v1"))
                .andExpect(jsonPath("$.promptVersion").value("v1"))
                .andExpect(jsonPath("$.cacheHit").value(false));
    }

    @Test
    void rejectsRequestWithoutRequiredIds() throws Exception {
        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(analysisService);
    }

    @Test
    void returnsNotFoundWhenResumeDoesNotExist() throws Exception {
        when(analysisService.create(any(CreateAnalysisRequest.class)))
                .thenThrow(new ResourceNotFoundException("Resume not found: 404"));

        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resumeId": 404,
                                  "jobPostingId": 9
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Resume not found: 404"));
    }

    @Test
    void returnsNotFoundWhenJobPostingDoesNotExist() throws Exception {
        when(analysisService.create(any(CreateAnalysisRequest.class)))
                .thenThrow(new ResourceNotFoundException("Job posting not found: 404"));

        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resumeId": 4,
                                  "jobPostingId": 404
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Job posting not found: 404"));
    }

    @Test
    void returnsSafeProviderError() throws Exception {
        when(analysisService.create(any(CreateAnalysisRequest.class)))
                .thenThrow(new NvidiaProviderException(
                        HttpStatus.BAD_GATEWAY,
                        NvidiaProviderException.Reason.INVALID_RESPONSE,
                        "NVIDIA returned an invalid analysis response.",
                        AnalysisValidationFailure.UNSUPPORTED_EVIDENCE
                ));

        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resumeId": 4,
                                  "jobPostingId": 9
                                }
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value("NVIDIA returned an invalid analysis response."))
                .andExpect(jsonPath("$.validationFailure").doesNotExist());
    }

    @Test
    void returnsAllAnalyses() throws Exception {
        when(analysisService.findAll()).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/analyses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].summary").value("One matched skill and one missing skill."));
    }

    @Test
    void returnsAnalysisById() throws Exception {
        when(analysisService.findById(1L)).thenReturn(response());

        mockMvc.perform(get("/api/analyses/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()))
                .andExpect(jsonPath("$.result.recommendedActions[0]").value(
                        "Develop or document Docker experience before claiming it in an application."
                ));
    }

    @Test
    void returnsNotFoundWhenAnalysisDoesNotExist() throws Exception {
        when(analysisService.findById(404L))
                .thenThrow(new ResourceNotFoundException("Analysis not found: 404"));

        mockMvc.perform(get("/api/analyses/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Analysis not found: 404"));
    }

    private AnalysisResponse response() {
        AnalysisResult result = new AnalysisResult(
                50,
                "One matched skill and one missing skill.",
                List.of(
                        new SkillAssessment("Java", MatchStatus.MATCHED, "Java experience", "Matched."),
                        new SkillAssessment("Docker", MatchStatus.MISSING, null, "Missing.")
                ),
                List.of("Java is supported by resume evidence."),
                List.of("Docker is requested but is not evidenced in the resume."),
                List.of("Develop or document Docker experience before claiming it in an application.")
        );

        return new AnalysisResponse(
                1L,
                4L,
                9L,
                result.matchScore(),
                result.summary(),
                result,
                "fake",
                "keyword-matcher-v1",
                "v1",
                CREATED_AT,
                false
        );
    }
}
