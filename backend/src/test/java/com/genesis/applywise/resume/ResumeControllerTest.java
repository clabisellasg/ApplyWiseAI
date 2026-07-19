package com.genesis.applywise.resume;

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
import static org.mockito.Mockito.verify;
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

@WebMvcTest(ResumeController.class)
@Import(GlobalExceptionHandler.class)
class ResumeControllerTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-10T08:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-01-11T09:30:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ResumeService resumeService;

    @Test
    void createsResume() throws Exception {
        when(resumeService.create(any(CreateResumeRequest.class))).thenReturn(response());

        mockMvc.perform(post("/api/resumes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Primary Resume",
                                  "targetRole": "Software Engineer",
                                  "content": "Experienced Java developer"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/resumes/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Primary Resume"))
                .andExpect(jsonPath("$.targetRole").value("Software Engineer"));
    }

    @Test
    void rejectsInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/api/resumes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": " ",
                                  "targetRole": "IT Support",
                                  "content": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(resumeService);
    }

    @Test
    void returnsAllResumes() throws Exception {
        when(resumeService.findAll()).thenReturn(List.of(response()));

        mockMvc.perform(get("/api/resumes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].content").value("Experienced Java developer"));
    }

    @Test
    void returnsResumeById() throws Exception {
        when(resumeService.findById(1L)).thenReturn(response());

        mockMvc.perform(get("/api/resumes/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Primary Resume"))
                .andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()));
    }

    @Test
    void returnsNotFoundWhenResumeDoesNotExist() throws Exception {
        when(resumeService.findById(99L))
                .thenThrow(new ResourceNotFoundException("Resume not found: 99"));

        mockMvc.perform(get("/api/resumes/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Resume not found: 99"));
    }

    @Test
    void updatesResume() throws Exception {
        when(resumeService.update(any(Long.class), any(UpdateResumeRequest.class))).thenReturn(response());

        mockMvc.perform(put("/api/resumes/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Primary Resume",
                                  "targetRole": "Software Engineer",
                                  "content": "Experienced Java developer"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.updatedAt").value(UPDATED_AT.toString()));
    }

    @Test
    void deletesResume() throws Exception {
        mockMvc.perform(delete("/api/resumes/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(resumeService).delete(1L);
    }

    private ResumeResponse response() {
        return new ResumeResponse(
                1L,
                "Primary Resume",
                "Software Engineer",
                "Experienced Java developer",
                CREATED_AT,
                UPDATED_AT
        );
    }
}
