package org.alim.observability.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Assigns each request a correlation ID and writes one structured log line
 * when the request completes. Ordered after Spring's
 * ServerHttpObservationFilter (HIGHEST_PRECEDENCE + 1) so the trace context,
 * and with it traceId/spanId in the MDC, already exists; ordered before
 * Spring Security so rejected requests are logged too.
 * <p>
 * Field names follow ECS to match the structured log format.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    static final String REQUEST_ID_MDC_KEY = "requestId";

    // Client-supplied IDs end up in every log line, so only accept short, plain values.
    private static final Pattern VALID_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Prometheus scrapes every 10s; logging those requests is pure noise.
        return request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestId = resolveRequestId(request);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        long start = System.nanoTime();

        try (MDC.MDCCloseable ignored = MDC.putCloseable(REQUEST_ID_MDC_KEY, requestId)) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                logCompletion(request, response, System.nanoTime() - start);
            }
        }
    }

    private static String resolveRequestId(HttpServletRequest request) {

        String requestId = request.getHeader(REQUEST_ID_HEADER);

        if (requestId != null && VALID_REQUEST_ID.matcher(requestId).matches()) {
            return requestId;
        }

        return UUID.randomUUID().toString();
    }

    private static void logCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            long durationNanos
    ) {

        Object route = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);

        log.atInfo()
                .addKeyValue("http.request.method", request.getMethod())
                .addKeyValue("url.path", request.getRequestURI())
                .addKeyValue("http.route", route != null ? route : "UNMATCHED")
                .addKeyValue("http.response.status_code", response.getStatus())
                .addKeyValue("event.duration", durationNanos)
                .log("HTTP {} {} completed with status {}", request.getMethod(), request.getRequestURI(), response.getStatus());
    }
}
