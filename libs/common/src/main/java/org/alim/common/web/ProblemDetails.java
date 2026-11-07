package org.alim.common.web;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/**
 * Builds RFC 9457 problem documents ({@code application/problem+json}) with the two
 * extension members every service in this system adds: a stable machine-readable
 * {@code code} and a {@code timestamp}.
 */
public final class ProblemDetails {

    public static final String CODE = "code";

    public static final String TIMESTAMP = "timestamp";

    private ProblemDetails() {
    }

    public static ProblemDetail of(HttpStatusCode status, String code, String detail) {

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE, code);
        problem.setProperty(TIMESTAMP, Instant.now());

        return problem;
    }

    /**
     * Adds {@code code} (derived from the status) and {@code timestamp} if not already present.
     */
    static ProblemDetail enrich(ProblemDetail problem) {

        if (problem.getProperties() == null || !problem.getProperties().containsKey(CODE)) {
            HttpStatus status = HttpStatus.resolve(problem.getStatus());
            problem.setProperty(CODE, status != null ? status.name() : "ERROR");
        }

        if (problem.getProperties() == null || !problem.getProperties().containsKey(TIMESTAMP)) {
            problem.setProperty(TIMESTAMP, Instant.now());
        }

        return problem;
    }
}
