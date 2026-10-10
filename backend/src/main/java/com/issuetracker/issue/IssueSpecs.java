package com.issuetracker.issue;

import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

/** Small reusable WHERE-clause pieces, combined with .and(...) in IssueService. */
public final class IssueSpecs {

    private IssueSpecs() {
    }

    public static Specification<Issue> inProject(Long projectId) {
        return (root, query, cb) -> cb.equal(root.get("project").get("id"), projectId);
    }

    public static Specification<Issue> hasStatus(IssueStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Issue> hasType(IssueType type) {
        return (root, query, cb) -> cb.equal(root.get("type"), type);
    }

    public static Specification<Issue> assignedTo(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("assignee").get("id"), userId);
    }

    public static Specification<Issue> unassigned() {
        return (root, query, cb) -> cb.isNull(root.get("assignee"));
    }

    public static Specification<Issue> inSprint(Long sprintId) {
        return (root, query, cb) -> cb.equal(root.get("sprint").get("id"), sprintId);
    }

    public static Specification<Issue> inBacklog() {
        return (root, query, cb) -> cb.isNull(root.get("sprint"));
    }

    public static Specification<Issue> textContains(String text) {
        String escaped = text.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        String pattern = "%" + escaped + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.<String>get("title")), pattern, '\\'),
                cb.like(cb.lower(root.<String>get("description")), pattern, '\\'));
    }
}
