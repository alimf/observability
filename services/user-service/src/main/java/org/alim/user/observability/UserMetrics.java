package org.alim.user.observability;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Exported to Prometheus as {@code users_registered_total} and
 * {@code users_logins_total{outcome="success|failure"}}.
 */
@Component
public class UserMetrics {

    private final Counter registered;

    private final Counter loginSuccess;

    private final Counter loginFailure;

    public UserMetrics(MeterRegistry registry) {

        registered = Counter.builder("users.registered")
                .description("Number of users registered")
                .register(registry);

        loginSuccess = Counter.builder("users.logins")
                .description("Login attempts by outcome")
                .tag("outcome", "success")
                .register(registry);

        loginFailure = Counter.builder("users.logins")
                .description("Login attempts by outcome")
                .tag("outcome", "failure")
                .register(registry);
    }

    public void userRegistered() {
        registered.increment();
    }

    public void loginSucceeded() {
        loginSuccess.increment();
    }

    public void loginFailed() {
        loginFailure.increment();
    }
}
