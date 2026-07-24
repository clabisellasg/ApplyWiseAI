package com.genesis.applywise.application;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/applications")
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    public JobApplicationController(JobApplicationService jobApplicationService) {
        this.jobApplicationService = jobApplicationService;
    }

    @PostMapping
    public ResponseEntity<JobApplicationResponse> create(
            @Valid @RequestBody CreateApplicationRequest request
    ) {
        JobApplicationResponse response = jobApplicationService.create(request);
        return ResponseEntity
                .created(URI.create("/api/applications/" + response.id()))
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<JobApplicationResponse>> findAll(
            @RequestParam(required = false) ApplicationStatus status
    ) {
        return ResponseEntity.ok(jobApplicationService.findAll(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobApplicationResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(jobApplicationService.findById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobApplicationResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateApplicationRequest request
    ) {
        return ResponseEntity.ok(jobApplicationService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<JobApplicationResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateApplicationStatusRequest request
    ) {
        return ResponseEntity.ok(jobApplicationService.updateStatus(id, request));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<ApplicationStatusHistoryResponse>> findHistory(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(jobApplicationService.findHistory(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        jobApplicationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
