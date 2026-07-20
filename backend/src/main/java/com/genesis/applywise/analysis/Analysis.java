package com.genesis.applywise.analysis;

import com.genesis.applywise.ai.AnalysisResult;
import com.genesis.applywise.job.JobPosting;
import com.genesis.applywise.resume.Resume;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "analyses")
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Column(name = "match_score", nullable = false)
    private int matchScore;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", nullable = false, columnDefinition = "jsonb")
    private AnalysisResult result;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(nullable = false, length = 128)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 64)
    private String promptVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Analysis() {
    }

    Analysis(
            Resume resume,
            JobPosting jobPosting,
            AnalysisResult result,
            String provider,
            String model,
            String promptVersion
    ) {
        this.resume = resume;
        this.jobPosting = jobPosting;
        this.matchScore = result.matchScore();
        this.summary = result.summary();
        this.result = result;
        this.provider = provider;
        this.model = model;
        this.promptVersion = promptVersion;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Resume getResume() {
        return resume;
    }

    public JobPosting getJobPosting() {
        return jobPosting;
    }

    public int getMatchScore() {
        return matchScore;
    }

    public String getSummary() {
        return summary;
    }

    public AnalysisResult getResult() {
        return result;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
