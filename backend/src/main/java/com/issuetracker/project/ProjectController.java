package com.issuetracker.project;

import com.issuetracker.project.ProjectDtos.AddMemberRequest;
import com.issuetracker.project.ProjectDtos.ChangeRoleRequest;
import com.issuetracker.project.ProjectDtos.CreateProjectRequest;
import com.issuetracker.project.ProjectDtos.MemberResponse;
import com.issuetracker.project.ProjectDtos.ProjectResponse;
import com.issuetracker.project.ProjectDtos.UpdateProjectRequest;
import com.issuetracker.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public List<ProjectResponse> myProjects(@AuthenticationPrincipal AuthUser me) {
        return projectService.listMine(me.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest req, @AuthenticationPrincipal AuthUser me) {
        return projectService.create(me.id(), req);
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@PathVariable Long projectId, @AuthenticationPrincipal AuthUser me) {
        return projectService.get(projectId, me.id());
    }

    @PutMapping("/{projectId}")
    public ProjectResponse update(@PathVariable Long projectId, @Valid @RequestBody UpdateProjectRequest req,
                                  @AuthenticationPrincipal AuthUser me) {
        return projectService.update(projectId, me.id(), req);
    }

    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long projectId, @AuthenticationPrincipal AuthUser me) {
        projectService.delete(projectId, me.id());
    }

    // ---- members

    @GetMapping("/{projectId}/members")
    public List<MemberResponse> members(@PathVariable Long projectId, @AuthenticationPrincipal AuthUser me) {
        return projectService.listMembers(projectId, me.id());
    }

    @PostMapping("/{projectId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public MemberResponse addMember(@PathVariable Long projectId, @Valid @RequestBody AddMemberRequest req,
                                    @AuthenticationPrincipal AuthUser me) {
        return projectService.addMember(projectId, me.id(), req);
    }

    @PutMapping("/{projectId}/members/{userId}")
    public MemberResponse changeRole(@PathVariable Long projectId, @PathVariable Long userId,
                                     @Valid @RequestBody ChangeRoleRequest req, @AuthenticationPrincipal AuthUser me) {
        return projectService.changeRole(projectId, me.id(), userId, req);
    }

    @DeleteMapping("/{projectId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable Long projectId, @PathVariable Long userId,
                             @AuthenticationPrincipal AuthUser me) {
        projectService.removeMember(projectId, me.id(), userId);
    }
}
