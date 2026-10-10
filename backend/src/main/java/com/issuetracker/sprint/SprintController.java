package com.issuetracker.sprint;

import com.issuetracker.security.AuthUser;
import com.issuetracker.sprint.SprintService.CreateSprintRequest;
import com.issuetracker.sprint.SprintService.SprintResponse;
import com.issuetracker.sprint.SprintService.StartSprintRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/sprints")
public class SprintController {

    private final SprintService sprintService;

    public SprintController(SprintService sprintService) {
        this.sprintService = sprintService;
    }

    @GetMapping
    public List<SprintResponse> list(@PathVariable Long projectId, @AuthenticationPrincipal AuthUser me) {
        return sprintService.list(projectId, me.id());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SprintResponse create(@PathVariable Long projectId, @Valid @RequestBody CreateSprintRequest req,
                                 @AuthenticationPrincipal AuthUser me) {
        return sprintService.create(projectId, me.id(), req);
    }

    @PostMapping("/{sprintId}/start")
    public SprintResponse start(@PathVariable Long projectId, @PathVariable Long sprintId,
                                @RequestBody(required = false) StartSprintRequest req,
                                @AuthenticationPrincipal AuthUser me) {
        return sprintService.start(projectId, sprintId, me.id(), req);
    }

    @PostMapping("/{sprintId}/complete")
    public SprintResponse complete(@PathVariable Long projectId, @PathVariable Long sprintId,
                                   @AuthenticationPrincipal AuthUser me) {
        return sprintService.complete(projectId, sprintId, me.id());
    }

    @DeleteMapping("/{sprintId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long projectId, @PathVariable Long sprintId,
                       @AuthenticationPrincipal AuthUser me) {
        sprintService.delete(projectId, sprintId, me.id());
    }
}
