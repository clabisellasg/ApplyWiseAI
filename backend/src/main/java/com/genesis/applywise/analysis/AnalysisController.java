package com.genesis.applywise.analysis;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping
    public ResponseEntity<AnalysisResponse> create(@Valid @RequestBody CreateAnalysisRequest request) {
        AnalysisResponse response = analysisService.create(request);
        return ResponseEntity.created(URI.create("/api/analyses/" + response.id())).body(response);
    }

    @GetMapping
    public ResponseEntity<List<AnalysisResponse>> findAll() {
        return ResponseEntity.ok(analysisService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AnalysisResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(analysisService.findById(id));
    }
}
