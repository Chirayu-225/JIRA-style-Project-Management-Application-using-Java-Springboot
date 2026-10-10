package com.issuetracker.activity;

import com.issuetracker.common.PageResponse;
import com.issuetracker.project.ProjectAccess;
import com.issuetracker.project.ProjectRole;
import com.issuetracker.user.User;
import com.issuetracker.user.UserSummary;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ActivityService {

    public record ActivityResponse(Long id, Long issueId, String action, String details,
                                   UserSummary actor, Instant createdAt) {
        static ActivityResponse from(ActivityLog a) {
            return new ActivityResponse(a.getId(), a.getIssueId(), a.getAction(), a.getDetails(),
                    UserSummary.from(a.getActor()), a.getCreatedAt());
        }
    }

    private final ActivityRepository repository;
    private final ProjectAccess access;

    public ActivityService(ActivityRepository repository, ProjectAccess access) {
        this.repository = repository;
        this.access = access;
    }

    /** Called by other services inside THEIR transaction, so the log and the change commit together. */
    public void record(Long projectId, Long issueId, User actor, String action, String details) {
        repository.save(new ActivityLog(projectId, issueId, actor, action, details));
    }

    @Transactional(readOnly = true)
    public PageResponse<ActivityResponse> forProject(Long projectId, Long userId, int page, int size) {
        access.require(projectId, userId, ProjectRole.VIEWER);
        var result = repository.findByProjectIdOrderByCreatedAtDesc(projectId,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        return PageResponse.from(result.map(ActivityResponse::from));
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> forIssue(Long projectId, Long issueId, Long userId) {
        access.require(projectId, userId, ProjectRole.VIEWER);
        return repository.findByProjectIdAndIssueIdOrderByCreatedAtDesc(projectId, issueId)
                .stream().map(ActivityResponse::from).toList();
    }
}
