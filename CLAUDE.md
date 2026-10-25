# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Spring Boot 3.4.2 sample order service (create/get orders, PostgreSQL + Flyway) whose real purpose is to exercise a
local observability stack: Prometheus metrics, OpenTelemetry traces to Tempo, Grafana dashboards, and Alertmanager
alerts. Payment is simulated (`SimulatedPaymentClient`: 10% decline rate, 20–150 ms latency) so failures, retries, and
circuit-breaker trips actually happen.

## Commands

```bash
docker compose up -d                 # Postgres, Prometheus, Alertmanager, Tempo, Grafana (app is NOT in compose)
./gradlew bootRun                    # run app on :8080 (needs Postgres from compose)
./gradlew build                      # compile + test
./gradlew test                       # all tests; uses in-memory H2, no compose services needed
./gradlew test --tests 'org.alim.observability.order.service.OrderServiceTest'
./gradlew test --tests 'org.alim.observability.order.service.OrderServiceTest.someMethod'
```

There is no linter/formatter configured.

Toolchain is JDK 27, but `options.release = 21` — compiled bytecode targets Java 21 because Spring Boot 3.4.2's
Spring Framework doesn't support newer class files. Don't use Java language/API features newer than 21.

## Architecture

Package root `org.alim.observability`:

- `order.api` — `OrderController` (`/api/v1/orders`), request/response records, and `GlobalExceptionHandler`, which
  maps exceptions to `ApiError` JSON (`code`, `message`, `timestamp`): validation → 400, `OrderNotFoundException` → 404,
  `PaymentException` → 502, Resilience4j `CallNotPermittedException` (circuit open) → 503, anything else → 500.
- `order.service` — `OrderService` orchestrates: save order as `CREATED` → call `PaymentClient.authorize` → mark `PAID`
  or `FAILED`, recording metrics and timing around the payment call. `PaymentClient` is an interface; the only impl is
  `SimulatedPaymentClient`, annotated with `@Retry` and `@CircuitBreaker` named `paymentClient` (configured under
  `resilience4j.*.instances.paymentClient` in `application.yaml`).
- `order.domain` — `Order` JPA entity (UUID id, `@Version` optimistic locking, timestamps via `@PrePersist/@PreUpdate`),
  created only via `Order.create(...)`; status transitions via `markPaid()/markFailed()`.
- `order.config.SecurityConfig` — permits `/api/**` and `/actuator/**` unauthenticated (so Prometheus can scrape).
- `observability.OrderMetrics` — custom Micrometer meters: `orders.created`, `orders.creation.failures`,
  `orders.payment.success`, `orders.creation.duration` (timer with percentile histogram). Note the Prometheus client
  strips the reserved `_created` suffix, so `orders.created` is exported as `orders_total`.
- `observability.RequestLoggingFilter` — sets `requestId` in MDC (from/to `X-Request-Id`) and logs one line per
  completed request with ECS fields (`http.route`, `http.response.status_code`, `event.duration` in ns). Skips
  `/actuator/**`. `SimulatedPaymentClient.authorize` is `@Observed`, producing a `payment authorize` span per retry
  attempt.

Note: `OrderService`'s package-private `@Transactional` helpers (`createPendingOrder`, `markPaid`, `markFailed`) are
self-invoked, so their annotations have no proxy effect — everything runs in `create()`'s single transaction, which is
rolled back when the payment exception is rethrown.

### Cross-cutting coupling to keep in sync

- **Metric names** are referenced in Prometheus form (dots → underscores, `_total` / `_seconds_bucket` suffixes) by
  `deploy/prometheus/alerts.yml` and `deploy/grafana/dashboards/orders-dashboard.json`. Renaming a meter or the
  `paymentClient` Resilience4j instance breaks alerts/dashboards. Alerts also depend on the Prometheus job name
  `observability-app` in `deploy/prometheus/prometheus.yml`.
- **Schema**: Flyway owns DDL (`src/main/resources/db/migration`, naming `V1.001__...sql`) in schema `app_schema`;
  Hibernate runs with `ddl-auto: validate`, so entity changes need a matching new migration.
- **Tests** override config in `src/test/resources/application.yaml`: H2 in PostgreSQL mode, `ddl-auto: create-drop`,
  tracing sampling 0.

## Local stack wiring

Prometheus (in Docker) scrapes the host app at `host.docker.internal:8080/actuator/prometheus`. The app exports traces
over OTLP HTTP to `http://localhost:4318/v1/traces` (Tempo); override with `OTEL_EXPORTER_OTLP_ENDPOINT` and
`TRACING_SAMPLING_PROBABILITY` (default 1.0 so every logged `traceId` resolves in Tempo). Grafana (:3000, anonymous
viewer) is provisioned from
`deploy/grafana/provisioning`. Alertmanager's receiver is intentionally empty. Logs are structured ECS JSON (Spring
Boot's built-in
`StructuredLogEncoder`, flat dotted keys; MDC `traceId`/`spanId`/`requestId` become top-level fields) on the console and
in `logs/observability.log` (override dir with `LOG_PATH`; size-rotated).

Log pipeline: Alloy (`deploy/alloy/config.alloy`) tails `./logs/*.log` via a read-only bind mount and pushes to Loki
(`deploy/loki/loki.yaml`, single binary, filesystem, TSDB schema v13, 7-day retention). Indexed labels are only
`service_name`, `environment`, `level`, `source`; `trace_id`/`span_id`/`request_id` are Loki structured metadata, so
query
them as `{service_name="observability"} | trace_id="..."`, never as stream selectors. Lines that aren't JSON get
`level="unknown"`. Alloy does not log retried push failures — check `loki_write_*` metrics on `localhost:12345/metrics`.
Loki's image is distroless, so it has no Compose healthcheck; use `curl localhost:3100/ready`. Tempo needs the
`tempo-init` service to chown its volume (Tempo runs as UID 10001).
