package com.issuetracker.sprint;

import com.issuetracker.activity.ActivityService;
import com.issuetracker.common.ApiException;
import com.issuetracker.issue.IssueRepository;
import com.issuetracker.issue.IssueStatus;
import com.issuetracker.project.ProjectAccess;
import com.issuetracker.project.ProjectMember;
import com.issuetracker.project.ProjectRole;
import com.issuetracker.realtime.ProjectEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class SprintService {

    public record CreateSprintRequest(@NotBlank @Size(max = 100) String name, @Size(max = 2000) String goal) {
    }

    public record StartSprintRequest(LocalDate startDate, LocalDate endDate) {
    }

    public record SprintResponse(Long id, String name, String goal, SprintStatus status,
                                 LocalDate startDate, LocalDate endDate, Instant createdAt) {
        static SprintResponse from(Sprint s) {
            return new SprintResponse(s.getId(), s.getName(), s.getGoal(), s.getStatus(),
                    s.getStartDate(), s.getEndDate(), s.getCreatedAt());
        }
    }

    private final SprintRepository sprints;
    private final IssueRepository issues;
    private final ProjectAccess access;
    private final ActivityService activity;
    private final ApplicationEventPublisher events;

    public SprintService(SprintRepository sprints, IssueRepository issues, ProjectAccess access,
                         ActivityService activity, ApplicationEventPublisher events) {
        this.sprints = sprints;
        this.issues = issues;
        this.access = access;
        this.activity = activity;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<SprintResponse> list(Long projectId, Long userId) {
        access.require(projectId, userId, ProjectRole.VIEWER);
        return sprints.findByProjectIdOrderByCreatedAtAsc(projectId).stream().map(SprintResponse::from).toList();
    }

    @Transactional
    public SprintResponse create(Long projectId, Long userId, CreateSprintRequest req) {
        ProjectMember me = access.require(projectId, userId, ProjectRole.MEMBER);
        String goal = (req.goal() == null || req.goal().isBlank()) ? null : req.goal().trim();
        Sprint sprint = sprints.save(new Sprint(me.getProject(), req.name().trim(), goal));
        activity.record(projectId, null, me.getUser(), "sprint.created", "Created " + sprint.getName());
        publish(projectId, userId, "Sprint " + sprint.getName() + " created");
        return SprintResponse.from(sprint);
    }

    @Transactional
    public SprintResponse start(Long projectId, Long sprintId, Long userId, StartSprintRequest req) {
        ProjectMember me = access.require(projectId, userId, ProjectRole.ADMIN);
        Sprint sprint = find(projectId, sprintId);
        // Friendly check here; the partial unique index in the DB is the real guarantee under concurrency.
        if (sprints.existsByProjectIdAndStatus(projectId, SprintStatus.ACTIVE)) {
            throw ApiException.conflict("Complete the active sprint before starting another one");
        }
        LocalDate start = (req != null && req.startDate() != null) ? req.startDate() : LocalDate.now();
        LocalDate end = (req != null && req.endDate() != null) ? req.endDate() : start.plusWeeks(2);
        if (end.isBefore(start)) {
            throw ApiException.badRequest("End date must be after start date");
        }
        try {
            sprint.start(start, end);
        } catch (IllegalStateException e) {
            throw ApiException.conflict(e.getMessage());
        }
        sprints.flush(); // hit the unique index now, inside this method, so errors map cleanly
        activity.record(projectId, null, me.getUser(), "sprint.started", "Started " + sprint.getName());
        publish(projectId, userId, "Sprint " + sprint.getName() + " started");
        return SprintResponse.from(sprint);
    }

    /** Completes the sprint. Unfinished issues go back to the backlog, like JIRA. */
    @Transactional
    public SprintResponse complete(Long projectId, Long sprintId, Long userId) {
        ProjectMember me = access.require(projectId, userId, ProjectRole.ADMIN);
        Sprint sprint = find(projectId, sprintId);
        try {
            sprint.complete();
        } catch (IllegalStateException e) {
            throw ApiException.conflict(e.getMessage());
        }
        int moved = issues.moveUnfinishedToBacklog(sprintId, IssueStatus.DONE);
        activity.record(projectId, null, me.getUser(), "sprint.completed",
                "Completed " + sprint.getName() + " (" + moved + " unfinished issue(s) moved to backlog)");
        publish(projectId, userId, "Sprint " + sprint.getName() + " completed");
        return SprintResponse.from(sprint);
    }

    @Transactional
    public void delete(Long projectId, Long sprintId, Long userId) {
        ProjectMember me = access.require(projectId, userId, ProjectRole.ADMIN);
        Sprint sprint = find(projectId, sprintId);
        if (sprint.getStatus() == SprintStatus.ACTIVE) {
            throw ApiException.conflict("Complete the sprint before deleting it");
        }
        sprints.delete(sprint); // issues in it fall back to the backlog (ON DELETE SET NULL)
        activity.record(projectId, null, me.getUser(), "sprint.deleted", "Deleted " + sprint.getName());
        publish(projectId, userId, "Sprint " + sprint.getName() + " deleted");
    }

    private Sprint find(Long projectId, Long sprintId) {
        return sprints.findByIdAndProjectId(sprintId, projectId)
                .orElseThrow(() -> ApiException.notFound("Sprint not found"));
    }

    private void publish(Long projectId, Long userId, String message) {
        events.publishEvent(new ProjectEvent(projectId, "sprint.updated", null, userId, message));
    }
}
