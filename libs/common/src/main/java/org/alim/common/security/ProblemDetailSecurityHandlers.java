package org.alim.common.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import org.alim.common.web.ProblemDetails;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

/**
 * Security filters run before Spring MVC, so {@code @RestControllerAdvice} never sees
 * their 401/403s. This renders them as problem documents and keeps the standard
 * {@code WWW-Authenticate: Bearer ...} challenge from the Spring Security handlers.
 */
@RequiredArgsConstructor
public class ProblemDetailSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    private final BearerTokenAuthenticationEntryPoint entryPoint = new BearerTokenAuthenticationEntryPoint();

    private final BearerTokenAccessDeniedHandler accessDeniedHandler = new BearerTokenAccessDeniedHandler();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {

        entryPoint.commence(request, response, exception);

        write(
                response,
                ProblemDetails.of(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required")
        );
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException {

        accessDeniedHandler.handle(request, response, exception);

        write(
                response,
                ProblemDetails.of(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access is denied")
        );
    }

    private void write(HttpServletResponse response, ProblemDetail problem) throws IOException {

        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
