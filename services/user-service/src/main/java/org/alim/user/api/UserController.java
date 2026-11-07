package org.alim.user.api;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import org.alim.common.security.CurrentUser;
import org.alim.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * Public sign-up (see {@code app.security.public-paths}).
     */
    @PostMapping
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterUserRequest request
    ) {

        UserResponse response = userService.register(request);

        // Users are only readable through /me, so Location points at the caller's own profile.
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/me")
                .build()
                .toUri();

        return ResponseEntity
                .created(location)
                .body(response);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.get(CurrentUser.id(jwt));
    }
}
