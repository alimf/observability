package org.alim.user.service;

import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.alim.user.api.RegisterUserRequest;
import org.alim.user.api.UserResponse;
import org.alim.user.domain.EmailAlreadyRegisteredException;
import org.alim.user.domain.User;
import org.alim.user.domain.UserNotFoundException;
import org.alim.user.observability.UserMetrics;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMetrics metrics;

    @Transactional
    public UserResponse register(RegisterUserRequest request) {

        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = User.register(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim()
        );

        try {
            // Flush now so a concurrent duplicate surfaces here rather than at commit time.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyRegisteredException();
        }

        metrics.userRegistered();

        return UserResponse.from(user);
    }

    public UserResponse get(UUID id) {

        return userRepository
                .findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
