package com.issuetracker.project;

import com.issuetracker.common.ApiException;
import org.springframework.stereotype.Component;

/**
 * Central authorization check used by every service:
 * "is this user a member of this project with at least role X?"
 *
 * Non-members get 404 (not 403) so outsiders can't even discover that a project id exists.
 */
@Component
public class ProjectAccess {

    private final ProjectMemberRepository members;

    public ProjectAccess(ProjectMemberRepository members) {
        this.members = members;
    }

    public ProjectMember require(Long projectId, Long userId, ProjectRole minimumRole) {
        ProjectMember member = members.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> ApiException.notFound("Project not found"));
        if (!member.getRole().atLeast(minimumRole)) {
            throw ApiException.forbidden("This action needs the " + minimumRole + " role or higher");
        }
        return member;
    }
}
