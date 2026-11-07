package org.alim.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.alim.user.api.LoginRequest;
import org.alim.user.api.TokenResponse;
import org.alim.user.domain.InvalidCredentialsException;
import org.alim.user.domain.User;
import org.alim.user.observability.UserMetrics;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokenService;
    private final UserMetrics metrics;

    /**
     * Verified against when the email is unknown, so response time doesn't reveal which emails exist.
     */
    private final String unknownUserHash;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService tokenService,
            UserMetrics metrics
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.metrics = metrics;
        this.unknownUserHash = passwordEncoder.encode("unknown-user-timing-equalizer");
    }

    public TokenResponse login(LoginRequest request) {

        User user = userRepository
                .findByEmail(UserService.normalizeEmail(request.email()))
                .orElse(null);

        boolean passwordMatches = passwordEncoder.matches(
                request.password(),
                user != null ? user.getPasswordHash() : unknownUserHash
        );

        if (user == null || !passwordMatches) {
            metrics.loginFailed();
            throw new InvalidCredentialsException();
        }

        metrics.loginSucceeded();

        return tokenService.issue(user);
    }
}
