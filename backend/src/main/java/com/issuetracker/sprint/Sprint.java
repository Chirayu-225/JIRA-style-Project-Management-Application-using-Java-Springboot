package com.issuetracker.sprint;

import com.issuetracker.project.Project;
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
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "sprints")
public class Sprint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false)
    private String name;

    private String goal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SprintStatus status = SprintStatus.PLANNED;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Sprint() {
    }

    public Sprint(Project project, String name, String goal) {
        this.project = project;
        this.name = name;
        this.goal = goal;
    }

    /** State machine: PLANNED -> ACTIVE -> COMPLETED. No other transitions allowed. */
    public void start(LocalDate start, LocalDate end) {
        if (status != SprintStatus.PLANNED) {
            throw new IllegalStateException("Only a planned sprint can be started");
        }
        this.status = SprintStatus.ACTIVE;
        this.startDate = start;
        this.endDate = end;
    }

    public void complete() {
        if (status != SprintStatus.ACTIVE) {
            throw new IllegalStateException("Only an active sprint can be completed");
        }
        this.status = SprintStatus.COMPLETED;
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public String getName() { return name; }
    public String getGoal() { return goal; }
    public SprintStatus getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public Instant getCreatedAt() { return createdAt; }
}
