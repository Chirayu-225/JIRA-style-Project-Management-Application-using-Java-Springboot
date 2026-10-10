package com.issuetracker.project;

/**
 * Roles are ordered from least to most powerful, so "at least MEMBER" is a simple comparison.
 * VIEWER: read only | MEMBER: create/edit issues, comment | ADMIN: sprints, members | OWNER: delete project
 */
public enum ProjectRole {
    VIEWER, MEMBER, ADMIN, OWNER;

    public boolean atLeast(ProjectRole required) {
        return this.ordinal() >= required.ordinal();
    }
}
