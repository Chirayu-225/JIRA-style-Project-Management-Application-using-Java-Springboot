package com.issuetracker.activity;

import com.issuetracker.activity.ActivityService.ActivityResponse;
import com.issuetracker.common.PageResponse;
import com.issuetracker.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ActivityController {

    private final ActivityService activityService;

    public ActivityController(ActivityService activityService) {
        this.activityService = activityService;
    }

    @GetMapping("/api/projects/{projectId}/activity")
    public PageResponse<ActivityResponse> projectActivity(@PathVariable Long projectId,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "30") int size,
                                                          @AuthenticationPrincipal AuthUser me) {
        return activityService.forProject(projectId, me.id(), page, size);
    }

    @GetMapping("/api/projects/{projectId}/issues/{issueId}/activity")
    public List<ActivityResponse> issueActivity(@PathVariable Long projectId,
                                                @PathVariable Long issueId,
                                                @AuthenticationPrincipal AuthUser me) {
        return activityService.forIssue(projectId, issueId, me.id());
    }
}
