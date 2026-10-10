package com.issuetracker.project;

import com.issuetracker.user.UserSummary;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record CreateProjectRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z][A-Za-z0-9]{1,9}$",
                    message = "2-10 letters/digits, starting with a letter") String key,
            @NotBlank @Size(max = 100) String name,
            @Size(max = 2000) String description) {
    }

    public record UpdateProjectRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 2000) String description) {
    }

    public record ProjectResponse(Long id, String key, String name, String description,
                                  UserSummary owner, ProjectRole myRole, long memberCount, Instant createdAt) {
    }

    public record AddMemberRequest(@NotBlank @Email String email, @NotNull ProjectRole role) {
    }

    public record ChangeRoleRequest(@NotNull ProjectRole role) {
    }

    public record MemberResponse(UserSummary user, ProjectRole role, Instant joinedAt) {
        public static MemberResponse from(ProjectMember m) {
            return new MemberResponse(UserSummary.from(m.getUser()), m.getRole(), m.getJoinedAt());
        }
    }
}
