package com.issuetracker.issue;

import com.issuetracker.activity.ActivityService;
import com.issuetracker.common.ApiException;
import com.issuetracker.common.PageResponse;
import com.issuetracker.issue.IssueDtos.CommentRequest;
import com.issuetracker.issue.IssueDtos.CommentResponse;
import com.issuetracker.issue.IssueDtos.CreateIssueRequest;
import com.issuetracker.issue.IssueDtos.IssueResponse;
import com.issuetracker.issue.IssueDtos.MoveIssueRequest;
import com.issuetracker.issue.IssueDtos.UpdateIssueRequest;
import com.issuetracker.project.Project;
import com.issuetracker.project.ProjectAccess;
import com.issuetracker.project.ProjectMember;
import com.issuetracker.project.ProjectMemberRepository;
import com.issuetracker.project.ProjectRepository;
import com.issuetracker.project.ProjectRole;
import com.issuetracker.realtime.ProjectEvent;
import com.issuetracker.sprint.Sprint;
import com.issuetracker.sprint.SprintRepository;
import com.issuetracker.sprint.SprintStatus;
import com.issuetracker.user.User;
import com.issuetracker.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * All business rules for issues live here:
 * permissions, issue numbering, board ordering, change tracking and live-update events.
 */
@Service
public class IssueService {

    /** Spacing between neighbouring cards' positions. */
    private static final double GAP = 1000.0;
    /** "MOB-12" -> look the issue up by number instead of full-text searching. */
    private static final Pattern ISSUE_KEY = Pattern.compile("^[A-Za-z][A-Za-z0-9]*-(\\d{1,9})$");

    /** Optional board/backlog filters, all combinable. */
    public record IssueFilter(IssueStatus status, IssueType type, Long assigneeId, boolean unassigned,
                              Long sprintId, boolean backlog, String q) {
    }

    private final IssueRepository issues;
    private final CommentRepository comments;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final SprintRepository sprints;
    private final UserRepository users;
    private final ProjectAccess access;
    private final ActivityService activity;
    private final ApplicationEventPublisher events;

    public IssueService(IssueRepository issues, CommentRepository comments, ProjectRepository projects,
                        ProjectMemberRepository members, SprintRepository sprints, UserRepository users,
                        ProjectAccess access, ActivityService activity, ApplicationEventPublisher events) {
        this.issues = issues;
        this.comments = comments;
        this.projects = projects;
        this.members = members;
        this.sprints = sprints;
        this.users = users;
        this.access = access;
        this.activity = activity;
        this.events = events;
    }

    // ================================================================== queries

    @Transactional(readOnly = true)
    public PageResponse<IssueResponse> list(Long projectId, Long userId, IssueFilter f, int page, int size) {
        access.require(projectId, userId, ProjectRole.VIEWER);

        Specification<Issue> spec = IssueSpecs.inProject(projectId);
        if (f.status() != null) spec = spec.and(IssueSpecs.hasStatus(f.status()));
        if (f.type() != null) spec = spec.and(IssueSpecs.hasType(f.type()));
        if (f.unassigned()) spec = spec.and(IssueSpecs.unassigned());
        else if (f.assigneeId() != null) spec = spec.and(IssueSpecs.assignedTo(f.assigneeId()));
        if (f.backlog()) spec = spec.and(IssueSpecs.inBacklog());
        else if (f.sprintId() != null) spec = spec.and(IssueSpecs.inSprint(f.sprintId()));
        if (f.q() != null && !f.q().isBlank()) spec = spec.and(IssueSpecs.textContains(f.q().trim()));

        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 500),
                Sort.by("position").ascending().and(Sort.by("id").ascending()));
        return PageResponse.from(issues.findAll(spec, pageable).map(IssueResponse::from));
    }

    @Transactional(readOnly = true)
    public List<IssueResponse> search(Long projectId, Long userId, String q) {
        access.require(projectId, userId, ProjectRole.VIEWER);
        String query = q == null ? "" : q.trim();
        if (query.isEmpty()) {
            return List.of();
        }
        Matcher keyMatch = ISSUE_KEY.matcher(query);
        if (keyMatch.matches()) {
            return issues.findByProjectIdAndNumber(projectId, Integer.parseInt(keyMatch.group(1)))
                    .map(i -> List.of(IssueResponse.from(i)))
                    .orElse(List.of());
        }
        List<Long> ids = issues.searchIds(projectId, query);
        if (ids.isEmpty()) {
            return List.of();
        }
        // Second query loads full rows; keep the relevance order from the first.
        Map<Long, Issue> byId = issues.findByIdIn(ids).stream()
                .collect(Collectors.toMap(Issue::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).map(IssueResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public IssueResponse get(Long projectId, Long issueId, Long userId) {
        access.require(projectId, userId, ProjectRole.VIEWER);
        return IssueResponse.from(find(projectId, issueId));
    }

    // ================================================================== commands

    @Transactional
    public IssueResponse create(Long projectId, Long userId, CreateIssueRequest req) {
        ProjectMember me = access.require(projectId, userId, ProjectRole.MEMBER);
        // Row lock on the project: concurrent creates wait here, so numbers are never duplicated.
        Project project = projects.findByIdForUpdate(projectId)
                .orElseThrow(() -> ApiException.notFound("Project not found"));

        User assignee = resolveAssignee(projectId, req.assigneeId());
        Sprint sprint = resolveSprint(projectId, req.sprintId());
        IssueStatus status = req.status() == null ? IssueStatus.TODO : req.status();
        IssuePriority priority = req.priority() == null ? IssuePriority.MEDIUM : req.priority();
        double position = issues.maxPosition(projectId, status) + GAP; // new cards go to the bottom

        Issue issue = issues.save(new Issue(project, project.nextIssueNumber(), req.title().trim(),
                blankToNull(req.description()), req.type(), status, priority, req.storyPoints(),
                assignee, me.getUser(), sprint, position));

        activity.record(projectId, issue.getId(), me.getUser(), "issue.created",
                issue.getKey() + " " + issue.getTitle());
        publish(projectId, "issue.created", issue.getId(), userId,
                me.getUser().getFullName() + " created " + issue.getKey());
        return IssueResponse.from(issue);
    }

    @Transactional
    public IssueResponse update(Long projectId, Long issueId, Long userId, UpdateIssueRequest req) {
        ProjectMember me = access.require(projectId, userId, ProjectRole.MEMBER);
        Issue issue = find(projectId, issueId);

        // Optimistic locking, part 1: client says which version it edited.
        if (req.version() != null && !req.version().equals(issue.getVersion())) {
            throw ApiException.conflict("This issue was changed by someone else. Reload to see the latest version.");
        }

        List<String> changes = new ArrayList<>();
        String title = req.title().trim();
        if (!title.equals(issue.getTitle())) {
            changes.add("title");
            issue.setTitle(title);
        }
        String description = blankToNull(req.description());
        if (!Objects.equals(description, issue.getDescription())) {
            changes.add("description");
            issue.setDescription(description);
        }
        if (req.type() != issue.getType()) {
            changes.add("type: " + issue.getType() + " → " + req.type());
            issue.setType(req.type());
        }
        if (req.priority() != issue.getPriority()) {
            changes.add("priority: " + issue.getPriority() + " → " + req.priority());
            issue.setPriority(req.priority());
        }
        if (!Objects.equals(req.storyPoints(), issue.getStoryPoints())) {
            changes.add("points: " + issue.getStoryPoints() + " → " + req.storyPoints());
            issue.setStoryPoints(req.storyPoints());
        }
        if (req.status() != issue.getStatus()) {
            changes.add("status: " + issue.getStatus() + " → " + req.status());
            issue.setPosition(issues.maxPosition(projectId, req.status()) + GAP);
            issue.setStatus(req.status());
        }
        Long currentAssigneeId = issue.getAssignee() == null ? null : issue.getAssignee().getId();
        if (!Objects.equals(currentAssigneeId, req.assigneeId())) {
            User newAssignee = resolveAssignee(projectId, req.assigneeId());
            changes.add("assignee: " + nameOf(issue.getAssignee()) + " → " + nameOf(newAssignee));
            issue.setAssignee(newAssignee);
        }
        Long currentSprintId = issue.getSprint() == null ? null : issue.getSprint().getId();
        if (!Objects.equals(currentSprintId, req.sprintId())) {
            Sprint newSprint = resolveSprint(projectId, req.sprintId());
            changes.add("sprint: " + sprintName(issue.getSprint()) + " → " + sprintName(newSprint));
            issue.setSprint(newSprint);
        }

        if (changes.isEmpty()) {
            return IssueResponse.from(issue);
        }
        // Optimistic locking, part 2: flush now so the "WHERE version = ?" check runs here
        // and the response carries the NEW version number.
        issues.flush();
        activity.record(projectId, issue.getId(), me.getUser(), "issue.updated",
                issue.getKey() + ": " + String.join("; ", changes));
        publish(projectId, "issue.updated", issue.getId(), userId,
                me.getUser().getFullName() + " updated " + issue.getKey());
        return IssueResponse.from(issue);
    }

    /** Drag-and-drop on the board: change column and/or position. */
    @Transactional
    public IssueResponse move(Long projectId, Long issueId, Long userId, MoveIssueRequest req) {
        ProjectMember me = access.require(projectId, userId, ProjectRole.MEMBER);
        Issue issue = find(projectId, issueId);
        IssueStatus oldStatus = issue.getStatus();
        IssueStatus newStatus = req.status();

        Double above = neighbourPosition(projectId, req.afterIssueId(), issueId);
        Double below = neighbourPosition(projectId, req.beforeIssueId(), issueId);

        double newPosition;
        if (above == null && below == null) {
            newPosition = (oldStatus == newStatus) ? issue.getPosition()
                    : issues.maxPosition(projectId, newStatus) + GAP;
        } else if (above == null) {
            newPosition = below - GAP;
        } else if (below == null) {
            newPosition = above + GAP;
        } else {
            if (Math.abs(below - above) < 1e-6) {
                // Positions got too close after many inserts in the same spot: respace the column.
                rebalance(projectId, newStatus);
                above = neighbourPosition(projectId, req.afterIssueId(), issueId);
                below = neighbourPosition(projectId, req.beforeIssueId(), issueId);
            }
            newPosition = (above + below) / 2.0;
        }

        issue.setStatus(newStatus);
        issue.setPosition(newPosition);
        issues.flush();

        if (oldStatus != newStatus) {
            activity.record(projectId, issue.getId(), me.getUser(), "issue.moved",
                    issue.getKey() + " moved " + oldStatus + " → " + newStatus);
        }
        String message = oldStatus != newStatus
                ? me.getUser().getFullName() + " moved " + issue.getKey() + " to " + label(newStatus)
                : me.getUser().getFullName() + " reordered " + issue.getKey();
        publish(projectId, "issue.moved", issue.getId(), userId, message);
        return IssueResponse.from(issue);
    }

    @Transactional
    public void delete(Long projectId, Long issueId, Long userId) {
        ProjectMember me = access.require(projectId, userId, ProjectRole.MEMBER);
        Issue issue = find(projectId, issueId);
        boolean isReporter = issue.getReporter().getId().equals(userId);
        if (!isReporter && !me.getRole().atLeast(ProjectRole.ADMIN)) {
            throw ApiException.forbidden("Only the reporter or an admin can delete this issue");
        }
        String label = issue.getKey() + " " + issue.getTitle();
        issues.delete(issue); // comments are removed by ON DELETE CASCADE
        activity.record(projectId, null, me.getUser(), "issue.deleted", label);
        publish(projectId, "issue.deleted", issueId, userId,
                me.getUser().getFullName() + " deleted " + label);
    }

    // ================================================================== comments

    @Transactional(readOnly = true)
    public List<CommentResponse> listComments(Long projectId, Long issueId, Long userId) {
        access.require(projectId, userId, ProjectRole.VIEWER);
        find(projectId, issueId); // 404 if the issue isn't in this project
        return comments.findByIssueIdOrderByCreatedAtAsc(issueId).stream().map(CommentResponse::from).toList();
    }

    @Transactional
    public CommentResponse addComment(Long projectId, Long issueId, Long userId, CommentRequest req) {
        ProjectMember me = access.require(projectId, userId, ProjectRole.MEMBER);
        Issue issue = find(projectId, issueId);
        Comment comment = comments.save(new Comment(issue, me.getUser(), req.body().trim()));
        activity.record(projectId, issueId, me.getUser(), "comment.added", "Commented on " + issue.getKey());
        publish(projectId, "comment.created", issueId, userId,
                me.getUser().getFullName() + " commented on " + issue.getKey());
        return CommentResponse.from(comment);
    }

    @Transactional
    public void deleteComment(Long projectId, Long issueId, Long commentId, Long userId) {
        ProjectMember me = access.require(projectId, userId, ProjectRole.MEMBER);
        find(projectId, issueId);
        Comment comment = comments.findByIdAndIssueId(commentId, issueId)
                .orElseThrow(() -> ApiException.notFound("Comment not found"));
        if (!comment.getAuthor().getId().equals(userId) && !me.getRole().atLeast(ProjectRole.ADMIN)) {
            throw ApiException.forbidden("You can only delete your own comments");
        }
        comments.delete(comment);
        publish(projectId, "comment.deleted", issueId, userId, "A comment was removed");
    }

    // ================================================================== helpers

    private Issue find(Long projectId, Long issueId) {
        return issues.findByIdAndProjectId(issueId, projectId)
                .orElseThrow(() -> ApiException.notFound("Issue not found"));
    }

    private User resolveAssignee(Long projectId, Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        if (!members.existsByProjectIdAndUserId(projectId, assigneeId)) {
            throw ApiException.badRequest("The assignee must be a member of this project");
        }
        return users.getReferenceById(assigneeId);
    }

    private Sprint resolveSprint(Long projectId, Long sprintId) {
        if (sprintId == null) {
            return null;
        }
        Sprint sprint = sprints.findByIdAndProjectId(sprintId, projectId)
                .orElseThrow(() -> ApiException.badRequest("Sprint not found in this project"));
        if (sprint.getStatus() == SprintStatus.COMPLETED) {
            throw ApiException.badRequest("Can't add issues to a completed sprint");
        }
        return sprint;
    }

    private Double neighbourPosition(Long projectId, Long neighbourId, Long selfId) {
        if (neighbourId == null || neighbourId.equals(selfId)) {
            return null;
        }
        return issues.findByIdAndProjectId(neighbourId, projectId).map(Issue::getPosition).orElse(null);
    }

    private void rebalance(Long projectId, IssueStatus status) {
        List<Issue> column = issues.findByProjectIdAndStatusOrderByPositionAsc(projectId, status);
        for (int i = 0; i < column.size(); i++) {
            column.get(i).setPosition((i + 1) * GAP);
        }
    }

    private void publish(Long projectId, String type, Long issueId, Long actorId, String message) {
        events.publishEvent(new ProjectEvent(projectId, type, issueId, actorId, message));
    }

    /** IN_PROGRESS -> "In progress" for human-readable messages. */
    private static String label(IssueStatus status) {
        String s = status.name().replace('_', ' ').toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String nameOf(User user) {
        return user == null ? "Unassigned" : user.getFullName();
    }

    private static String sprintName(Sprint sprint) {
        return sprint == null ? "Backlog" : sprint.getName();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
