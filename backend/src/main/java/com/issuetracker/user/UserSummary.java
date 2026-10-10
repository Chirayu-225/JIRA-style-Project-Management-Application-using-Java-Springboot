package com.issuetracker.user;

/** Public view of a user. Never expose the entity itself (it holds the password hash). */
public record UserSummary(Long id, String fullName, String email) {

    public static UserSummary from(User user) {
        return user == null ? null : new UserSummary(user.getId(), user.getFullName(), user.getEmail());
    }
}
