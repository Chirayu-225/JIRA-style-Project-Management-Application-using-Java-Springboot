package com.issuetracker.issue;

import com.issuetracker.common.PageResponse;
import com.issuetracker.issue.IssueDtos.CommentRequest;
import com.issuetracker.issue.IssueDtos.CommentResponse;
import com.issuetracker.issue.IssueDtos.CreateIssueRequest;
import com.issuetracker.issue.IssueDtos.IssueResponse;
import com.issuetracker.issue.IssueDtos.MoveIssueRequest;
import com.issuetracker.issue.IssueDtos.UpdateIssueRequest;
import com.issuetracker.issue.IssueService.IssueFilter;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/issues")
public class IssueController {

    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    /** Example: GET /api/projects/1/issues?sprintId=3&assigneeId=7&q=login */
    @GetMapping
    public PageResponse<IssueResponse> list(@PathVariable Long projectId,
                                            @RequestParam(required = false) IssueStatus status,
                                            @RequestParam(required = false) IssueType type,
                                            @RequestParam(required = false) Long assigneeId,
                                            @RequestParam(defaultValue = "false") boolean unassigned,
                                            @RequestParam(required = false) Long sprintId,
                                            @RequestParam(defaultValue = "false") boolean backlog,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "200") int size,
                                            @AuthenticationPrincipal AuthUser me) {
        var filter = new IssueFilter(status, type, assigneeId, unassigned, sprintId, backlog, q);
        return issueService.list(projectId, me.id(), filter, page, size);
    }

    /** Ranked full-text search, or exact lookup for keys like "MOB-12". */
    @GetMapping("/search")
    public List<IssueResponse> search(@PathVariable Long projectId, @RequestParam String q,
                                      @AuthenticationPrincipal AuthUser me) {
        return issueService.search(projectId, me.id(), q);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssueResponse create(@PathVariable Long projectId, @Valid @RequestBody CreateIssueRequest req,
                                @AuthenticationPrincipal AuthUser me) {
        return issueService.create(projectId, me.id(), req);
    }

    @GetMapping("/{issueId}")
    public IssueResponse get(@PathVariable Long projectId, @PathVariable Long issueId,
                             @AuthenticationPrincipal AuthUser me) {
        return issueService.get(projectId, issueId, me.id());
    }

    @PutMapping("/{issueId}")
    public IssueResponse update(@PathVariable Long projectId, @PathVariable Long issueId,
                                @Valid @RequestBody UpdateIssueRequest req, @AuthenticationPrincipal AuthUser me) {
        return issueService.update(projectId, issueId, me.id(), req);
    }

    @PostMapping("/{issueId}/move")
    public IssueResponse move(@PathVariable Long projectId, @PathVariable Long issueId,
                              @Valid @RequestBody MoveIssueRequest req, @AuthenticationPrincipal AuthUser me) {
        return issueService.move(projectId, issueId, me.id(), req);
    }

    @DeleteMapping("/{issueId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long projectId, @PathVariable Long issueId,
                       @AuthenticationPrincipal AuthUser me) {
        issueService.delete(projectId, issueId, me.id());
    }

    // ---- comments

    @GetMapping("/{issueId}/comments")
    public List<CommentResponse> comments(@PathVariable Long projectId, @PathVariable Long issueId,
                                          @AuthenticationPrincipal AuthUser me) {
        return issueService.listComments(projectId, issueId, me.id());
    }

    @PostMapping("/{issueId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(@PathVariable Long projectId, @PathVariable Long issueId,
                                      @Valid @RequestBody CommentRequest req, @AuthenticationPrincipal AuthUser me) {
        return issueService.addComment(projectId, issueId, me.id(), req);
    }

    @DeleteMapping("/{issueId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long projectId, @PathVariable Long issueId,
                              @PathVariable Long commentId, @AuthenticationPrincipal AuthUser me) {
        issueService.deleteComment(projectId, issueId, commentId, me.id());
    }
}
