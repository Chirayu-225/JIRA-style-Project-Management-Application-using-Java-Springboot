package com.issuetracker.realtime;

/**
 * Something changed in a project. Services publish this; EventBroadcaster pushes it
 * to every browser currently watching that project's board.
 *
 * @param type e.g. "issue.created", "issue.updated", "issue.moved", "issue.deleted",
 *             "comment.created", "sprint.updated", "members.updated", "project.updated"
 */
public record ProjectEvent(Long projectId, String type, Long issueId, Long actorId, String message) {
}
