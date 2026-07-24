package com.genesis.applywise.application;

import com.genesis.applywise.analysis.Analysis;
import com.genesis.applywise.job.JobPosting;
import com.genesis.applywise.resume.Resume;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "job_applications",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_job_applications_job_posting",
                columnNames = "job_posting_id"
        )
)
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id")
    private Resume resume;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "latest_analysis_id")
    private Analysis latestAnalysis;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ApplicationStatus status;

    @Column(name = "applied_at")
    private LocalDate appliedAt;

    @Column(name = "next_action", length = 500)
    private String nextAction;

    @Column(name = "next_action_at")
    private OffsetDateTime nextActionAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobApplication() {
    }

    JobApplication(
            JobPosting jobPosting,
            Resume resume,
            Analysis latestAnalysis,
            ApplicationStatus status,
            LocalDate appliedAt,
            String nextAction,
            OffsetDateTime nextActionAt,
            String notes
    ) {
        this.jobPosting = jobPosting;
        this.resume = resume;
        this.latestAnalysis = latestAnalysis;
        this.status = status;
        this.appliedAt = appliedAt;
        this.nextAction = nextAction;
        this.nextActionAt = nextActionAt;
        this.notes = notes;
    }

    void updateDetails(
            Resume resume,
            Analysis latestAnalysis,
            LocalDate appliedAt,
            String nextAction,
            OffsetDateTime nextActionAt,
            String notes
    ) {
        this.resume = resume;
        this.latestAnalysis = latestAnalysis;
        this.appliedAt = appliedAt;
        this.nextAction = nextAction;
        this.nextActionAt = nextActionAt;
        this.notes = notes;
    }

    void changeStatus(ApplicationStatus status) {
        this.status = status;
    }

    void setAppliedAt(LocalDate appliedAt) {
        this.appliedAt = appliedAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public JobPosting getJobPosting() {
        return jobPosting;
    }

    public Resume getResume() {
        return resume;
    }

    public Analysis getLatestAnalysis() {
        return latestAnalysis;
    }

    public ApplicationStatus getStatus() {
        return status;
    }

    public LocalDate getAppliedAt() {
        return appliedAt;
    }

    public String getNextAction() {
        return nextAction;
    }

    public OffsetDateTime getNextActionAt() {
        return nextActionAt;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
