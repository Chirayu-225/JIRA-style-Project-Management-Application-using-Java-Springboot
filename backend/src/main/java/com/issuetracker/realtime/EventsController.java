package com.issuetracker.realtime;

import com.issuetracker.project.ProjectAccess;
import com.issuetracker.project.ProjectRole;
import com.issuetracker.security.AuthUser;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class EventsController {

    private final EventBroadcaster broadcaster;
    private final ProjectAccess access;

    public EventsController(EventBroadcaster broadcaster, ProjectAccess access) {
        this.broadcaster = broadcaster;
        this.access = access;
    }

    /** Browser: new EventSource(`${API}/api/projects/1/events?access_token=${jwt}`) */
    @GetMapping(path = "/api/projects/{projectId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long projectId, @AuthenticationPrincipal AuthUser me) {
        access.require(projectId, me.id(), ProjectRole.VIEWER);
        return broadcaster.subscribe(projectId);
    }
}
