package com.issuetracker.project;

import com.issuetracker.activity.ActivityService;
import com.issuetracker.common.ApiException;
import com.issuetracker.issue.IssueRepository;
import com.issuetracker.project.ProjectDtos.AddMemberRequest;
import com.issuetracker.project.ProjectDtos.ChangeRoleRequest;
import com.issuetracker.project.ProjectDtos.CreateProjectRequest;
import com.issuetracker.project.ProjectDtos.MemberResponse;
import com.issuetracker.project.ProjectDtos.ProjectResponse;
import com.issuetracker.project.ProjectDtos.UpdateProjectRequest;
import com.issuetracker.realtime.ProjectEvent;
import com.issuetracker.user.User;
import com.issuetracker.user.UserRepository;
import com.issuetracker.user.UserSummary;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final UserRepository users;
    private final IssueRepository issues;
    private final ProjectAccess access;
    private final ActivityService activity;
    private final ApplicationEventPublisher events;

    public ProjectService(ProjectRepository projects, ProjectMemberRepository members, UserRepository users,
                          IssueRepository issues, ProjectAccess access, ActivityService activity,
                          ApplicationEventPublisher events) {
        this.projects = projects;
        this.members = members;
        this.users = users;
        this.issues = issues;
        this.access = access;
        this.activity = activity;
        this.events = events;
    }

    // ------------------------------------------------------------------ projects

    @Transactional(readOnly = true)
    public List<ProjectResponse> listMine(Long userId) {
        List<ProjectMember> memberships = members.findAllForUser(userId);
        List<Long> ids = memberships.stream().map(m -> m.getProject().getId()).toList();
        Map<Long, Long> counts = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : members.countByProjectIds(ids)) {
                counts.put((Long) row[0], (Long) row[1]);
            }
        }
        return memberships.stream()
                .map(m -> toResponse(m.getProject(), m.getRole(), counts.getOrDefault(m.getProject().getId(), 1L)))
                .toList();
    }

    @Transactional
    public ProjectResponse create(Long userId, CreateProjectRequest req) {
        String key = req.key().trim().toUpperCase(Locale.ROOT);
        if (projects.existsByKeyIgnoreCase(key)) {
            throw ApiException.conflict("Project key " + key + " is already taken");
        }
        User owner = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Unknown user"));
        Project project = projects.save(new Project(key, req.name().trim(), blankToNull(req.description()), owner));
        members.save(new ProjectMember(project, owner, ProjectRole.OWNER));
        activity.record(project.getId(), null, owner, "project.created", "Created project " + project.getName());
        return toResponse(project, ProjectRole.OWNER, 1);
    }

    @Transactional(readOnly = true)
    public ProjectResponse get(Long projectId, Long userId) {
        ProjectMember me = access.require(projectId, userId, ProjectRole.VIEWER);
        return toResponse(me.getProject(), me.getRole(), members.countByProjectId(projectId));
    }

    @Transactional
    public ProjectResponse update(Long projectId, Long userId, UpdateProjectRequest req) {
        ProjectMember me = access.require(projectId, userId, ProjectRole.ADMIN);
        Project project = me.getProject();
        project.update(req.name().trim(), blankToNull(req.description()));
        activity.record(projectId, null, me.getUser(), "project.updated", "Updated project details");
        events.publishEvent(new ProjectEvent(projectId, "project.updated", null, userId, "Project details updated"));
        return toResponse(project, me.getRole(), members.countByProjectId(projectId));
    }

    @Transactional
    public void delete(Long projectId, Long userId) {
        ProjectMember me = access.require(projectId, userId, ProjectRole.OWNER);
        // ON DELETE CASCADE in the schema removes members, sprints, issues, comments and activity.
        projects.delete(me.getProject());
    }

    // ------------------------------------------------------------------ members

    @Transactional(readOnly = true)
    public List<MemberResponse> listMembers(Long projectId, Long userId) {
        access.require(projectId, userId, ProjectRole.VIEWER);
        return members.findAllForProject(projectId).stream().map(MemberResponse::from).toList();
    }

    @Transactional
    public MemberResponse addMember(Long projectId, Long userId, AddMemberRequest req) {
        ProjectMember me = access.require(projectId, userId, ProjectRole.ADMIN);
        if (req.role() == ProjectRole.OWNER) {
            throw ApiException.badRequest("A project has exactly one owner");
        }
        User newUser = users.findByEmailIgnoreCase(req.email().trim())
                .orElseThrow(() -> ApiException.notFound("No account uses that email. Ask them to sign up first."));
        if (members.existsByProjectIdAndUserId(projectId, newUser.getId())) {
            throw ApiException.conflict(newUser.getFullName() + " is already a member");
        }
        ProjectMember member = members.save(new ProjectMember(me.getProject(), newUser, req.role()));
        activity.record(projectId, null, me.getUser(), "member.added",
                "Added " + newUser.getFullName() + " as " + req.role());
        events.publishEvent(new ProjectEvent(projectId, "members.updated", null, userId,
                newUser.getFullName() + " joined the project"));
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse changeRole(Long projectId, Long userId, Long targetUserId, ChangeRoleRequest req) {
        ProjectMember me = access.require(projectId, userId, ProjectRole.ADMIN);
        ProjectMember target = members.findByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(() -> ApiException.notFound("Member not found"));
        if (target.getRole() == ProjectRole.OWNER || req.role() == ProjectRole.OWNER) {
            throw ApiException.badRequest("The owner role cannot be changed or given away here");
        }
        target.setRole(req.role());
        activity.record(projectId, null, me.getUser(), "member.role",
                target.getUser().getFullName() + " is now " + req.role());
        events.publishEvent(new ProjectEvent(projectId, "members.updated", null, userId, "Roles changed"));
        return MemberResponse.from(target);
    }

    /** Admins can remove anyone except the owner; any member can remove themselves (leave). */
    @Transactional
    public void removeMember(Long projectId, Long userId, Long targetUserId) {
        ProjectRole needed = userId.equals(targetUserId) ? ProjectRole.VIEWER : ProjectRole.ADMIN;
        ProjectMember me = access.require(projectId, userId, needed);
        ProjectMember target = members.findByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(() -> ApiException.notFound("Member not found"));
        if (target.getRole() == ProjectRole.OWNER) {
            throw ApiException.badRequest("The owner cannot be removed");
        }
        issues.unassignUserInProject(projectId, targetUserId);
        members.delete(target);
        activity.record(projectId, null, me.getUser(), "member.removed",
                "Removed " + target.getUser().getFullName());
        events.publishEvent(new ProjectEvent(projectId, "members.updated", null, userId,
                target.getUser().getFullName() + " left the project"));
    }

    // ------------------------------------------------------------------ helpers

    private ProjectResponse toResponse(Project p, ProjectRole myRole, long memberCount) {
        return new ProjectResponse(p.getId(), p.getKey(), p.getName(), p.getDescription(),
                UserSummary.from(p.getOwner()), myRole, memberCount, p.getCreatedAt());
    }

    static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
