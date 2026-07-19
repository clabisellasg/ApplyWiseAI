package com.genesis.applywise.resume;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping
    public ResponseEntity<ResumeResponse> create(@Valid @RequestBody CreateResumeRequest request) {
        ResumeResponse response = resumeService.create(request);
        return ResponseEntity.created(URI.create("/api/resumes/" + response.id())).body(response);
    }

    @GetMapping
    public ResponseEntity<List<ResumeResponse>> findAll() {
        return ResponseEntity.ok(resumeService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResumeResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(resumeService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ResumeResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateResumeRequest request
    ) {
        return ResponseEntity.ok(resumeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        resumeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
