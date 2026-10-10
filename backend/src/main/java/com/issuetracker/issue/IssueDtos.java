package com.issuetracker.issue;

import com.issuetracker.user.UserSummary;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class IssueDtos {

    private IssueDtos() {
    }

    public record CreateIssueRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 20000) String description,
            @NotNull IssueType type,
            IssuePriority priority,          // default MEDIUM
            IssueStatus status,              // default TODO
            @Min(0) @Max(100) Integer storyPoints,
            Long assigneeId,
            Long sprintId) {                 // null = backlog
    }

    /** Full replacement of the editable fields (PUT semantics). null assigneeId = unassigned. */
    public record UpdateIssueRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 20000) String description,
            @NotNull IssueType type,
            @NotNull IssuePriority priority,
            @NotNull IssueStatus status,
            @Min(0) @Max(100) Integer storyPoints,
            Long assigneeId,
            Long sprintId,
            Long version) {                  // the version the client last saw (optimistic locking)
    }

    /**
     * Drag-and-drop: "put this card in column {status}, directly below {afterIssueId}
     * and directly above {beforeIssueId}". Either neighbour may be null (top / bottom / empty column).
     */
    public record MoveIssueRequest(@NotNull IssueStatus status, Long afterIssueId, Long beforeIssueId) {
    }

    public record IssueResponse(Long id, String key, int number, String title, String description,
                                IssueType type, IssueStatus status, IssuePriority priority, Integer storyPoints,
                                UserSummary assignee, UserSummary reporter, Long sprintId, String sprintName,
                                double position, Long version, Instant createdAt, Instant updatedAt) {

        public static IssueResponse from(Issue i) {
            return new IssueResponse(i.getId(), i.getKey(), i.getNumber(), i.getTitle(), i.getDescription(),
                    i.getType(), i.getStatus(), i.getPriority(), i.getStoryPoints(),
                    UserSummary.from(i.getAssignee()), UserSummary.from(i.getReporter()),
                    i.getSprint() == null ? null : i.getSprint().getId(),
                    i.getSprint() == null ? null : i.getSprint().getName(),
                    i.getPosition(), i.getVersion(), i.getCreatedAt(), i.getUpdatedAt());
        }
    }

    public record CommentRequest(@NotBlank @Size(max = 5000) String body) {
    }

    public record CommentResponse(Long id, String body, UserSummary author, Instant createdAt) {
        public static CommentResponse from(Comment c) {
            return new CommentResponse(c.getId(), c.getBody(), UserSummary.from(c.getAuthor()), c.getCreatedAt());
        }
    }
}
