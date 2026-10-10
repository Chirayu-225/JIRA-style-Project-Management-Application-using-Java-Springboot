package com.issuetracker.security;

/**
 * The logged-in user, as read from the JWT. Controllers receive it with
 * {@code @AuthenticationPrincipal AuthUser me}.
 */
public record AuthUser(Long id, String email) {
}
