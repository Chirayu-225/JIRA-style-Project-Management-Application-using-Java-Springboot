package com.issuetracker.user;

import com.issuetracker.common.ApiException;
import com.issuetracker.security.JwtService;
import com.issuetracker.user.AuthDtos.AuthResponse;
import com.issuetracker.user.AuthDtos.LoginRequest;
import com.issuetracker.user.AuthDtos.RegisterRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = users.save(new User(email, passwordEncoder.encode(req.password()), req.fullName().trim()));
        return new AuthResponse(jwtService.issue(user), UserSummary.from(user));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        // Same error for "no such email" and "wrong password": don't reveal which emails exist.
        User user = users.findByEmailIgnoreCase(req.email().trim())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Invalid email or password"));
        return new AuthResponse(jwtService.issue(user), UserSummary.from(user));
    }

    @Transactional(readOnly = true)
    public UserSummary me(Long userId) {
        return users.findById(userId).map(UserSummary::from)
                .orElseThrow(() -> ApiException.unauthorized("User no longer exists"));
    }
}
