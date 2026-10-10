package com.issuetracker.issue;

import com.issuetracker.project.Project;
import com.issuetracker.sprint.Sprint;
import com.issuetracker.user.User;
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
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "issues")
public class Issue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false)
    private int number;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssuePriority priority;

    @Column(name = "story_points")
    private Integer storyPoints;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id")
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sprint_id")
    private Sprint sprint; // null = in the backlog

    /**
     * Fractional index for drag-and-drop ordering. To drop a card between positions
     * 1000 and 2000 we store 1500: one row updated per move, instead of renumbering the column.
     */
    @Column(nullable = false)
    private double position;

    /**
     * Optimistic locking: Hibernate adds "WHERE version = ?" to every UPDATE and bumps it.
     * If someone else saved first, 0 rows match and we return 409 Conflict instead of
     * silently overwriting their change ("lost update" problem).
     */
    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Issue() {
    }

    public Issue(Project project, int number, String title, String description, IssueType type,
                 IssueStatus status, IssuePriority priority, Integer storyPoints,
                 User assignee, User reporter, Sprint sprint, double position) {
        this.project = project;
        this.number = number;
        this.title = title;
        this.description = description;
        this.type = type;
        this.status = status;
        this.priority = priority;
        this.storyPoints = storyPoints;
        this.assignee = assignee;
        this.reporter = reporter;
        this.sprint = sprint;
        this.position = position;
    }

    public String getKey() {
        return project.getKey() + "-" + number;
    }

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public int getNumber() { return number; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public IssueType getType() { return type; }
    public IssueStatus getStatus() { return status; }
    public IssuePriority getPriority() { return priority; }
    public Integer getStoryPoints() { return storyPoints; }
    public User getAssignee() { return assignee; }
    public User getReporter() { return reporter; }
    public Sprint getSprint() { return sprint; }
    public double getPosition() { return position; }
    public Long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setType(IssueType type) { this.type = type; }
    public void setStatus(IssueStatus status) { this.status = status; }
    public void setPriority(IssuePriority priority) { this.priority = priority; }
    public void setStoryPoints(Integer storyPoints) { this.storyPoints = storyPoints; }
    public void setAssignee(User assignee) { this.assignee = assignee; }
    public void setSprint(Sprint sprint) { this.sprint = sprint; }
    public void setPosition(double position) { this.position = position; }
}
