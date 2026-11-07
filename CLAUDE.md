# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A Gradle multi-module microservice sample (API gateway, user service, order service) whose real purpose is to exercise
a local observability stack: Prometheus metrics, OpenTelemetry traces to Tempo, Grafana dashboards, and Alertmanager
alerts. Payment is simulated (`SimulatedPaymentClient`: 10% decline rate, 20–150 ms latency) so failures, retries, and
circuit-breaker trips actually happen. See README.md for the architecture diagram and API table.

## Commands

```bash
docker compose up -d                 # Postgres, Prometheus, Alertmanager, Tempo, Loki, Alloy, Grafana (services are NOT in compose)
./gradlew :services:user-service:bootRun    # :8081 (needs Postgres from compose)
./gradlew :services:order-service:bootRun   # :8082 (needs Postgres; fetches JWKS from user-service lazily)
./gradlew :services:api-gateway:bootRun     # :8080
./gradlew build                      # compile + test everything
./gradlew test                       # all tests; uses in-memory H2, no compose services needed
./gradlew :services:order-service:test --tests 'org.alim.order.service.OrderServiceTest'
./gradlew :services:order-service:test --tests 'org.alim.order.service.OrderServiceTest.someMethod'
```

There is no linter/formatter configured.

Toolchain is JDK 27, but `options.release = 21` (set once in the root `build.gradle`) — compiled bytecode targets Java
21
because Spring Boot 3.4.2's Spring Framework doesn't support newer class files. Don't use Java language/API features
newer than 21.

## Architecture

Modules (root `build.gradle` applies the shared Java/Lombok/test config; `services/*` get the Boot plugin, `libs/*` are
`java-library`). Package roots: `org.alim.common`, `org.alim.user`, `org.alim.order`, `org.alim.gateway`.

- `libs/common` — auto-configured (`META-INF/spring/...AutoConfiguration.imports` → `CommonAutoConfiguration`) for every
  servlet service: `RequestLoggingFilter` (sets `requestId` in MDC from/to `X-Request-Id`, one ECS log line per request,
  skips `/actuator/**`), `GlobalExceptionHandler` (lowest-precedence advice; RFC 9457 `ProblemDetail` with `code` and
  `timestamp`, validation adds `errors`), and a default stateless JWT resource-server `SecurityFilterChain`
  (`/actuator/**` open; extra open paths via `app.security.public-paths`, entries are `"<pattern>"` or
  `"<METHOD> <pattern>"`; authorities from the `roles` claim). Defining your own `SecurityFilterChain` opts out.
  Service-specific advices must be `@Order(0)` so they beat the common catch-all `Exception` handler, and must not
  extend `ResponseEntityExceptionHandler` (the common one does). `CurrentUser.id(jwt)` is how services get the acting
  user (token `sub`, a UUID).
- `services/user-service` (:8081) — `User` entity (`users` table, bcrypt via delegating encoder), `POST /api/v1/users`
  (register), `POST /api/v1/auth/login` (RS256 token, OAuth-style `access_token/token_type/expires_in` body),
  `GET /api/v1/users/me`, `GET /.well-known/jwks.json`. `JwtConfig` loads `app.jwt.private-key/public-key` PEMs or
  generates an ephemeral pair (dev only); it also defines the `JwtDecoder` for its own `/me`. Login runs a dummy bcrypt
  check for unknown emails (timing) and returns the same `INVALID_CREDENTIALS` either way. Metrics: `users.registered`,
  `users.logins{outcome}`.
- `services/order-service` (:8082) — `OrderController` (`/api/v1/orders`: POST create, GET list (paged, newest first),
  GET by id, PATCH `{status: CANCELLED}`). The customer is always the token subject (`CreateOrderRequest` has no
  customer field); lookups use `findByIdAndCustomerId`, so foreign orders are `404`. `OrderService.create` saves the
  order as `CREATED` → `PaymentClient.authorize` → `PAID`/`FAILED`, recording metrics and timing around the payment
  call. `SimulatedPaymentClient` is `@Retry` + `@CircuitBreaker` named `paymentClient`
  (`resilience4j.*.instances.paymentClient`) and `@Observed` (one `payment authorize` span per retry attempt).
  `OrderMetrics`: `orders.created`, `orders.creation.failures`, `orders.payment.success`, `orders.creation.duration`
  (Prometheus client strips the reserved `_created` suffix, so `orders.created` is exported as `orders_total`).
  Validates tokens against `USER_SERVICE_JWKS_URI` (`jwk-set-uri`) plus `issuer-uri`/`audiences` as claim checks (no
  OIDC discovery). Deliberately has no Data REST dependency: it would auto-export repositories and bypass ownership.
- `services/api-gateway` (:8080) — Spring Cloud Gateway (WebFlux, does NOT depend on `libs/common`). Static routes by
  path to user/order service (`USER_SERVICE_URL`, `ORDER_SERVICE_URL`), `RequestIdFilter` ensures `X-Request-Id`. No
  auth at the gateway; services enforce it themselves.

Note: `OrderService`'s package-private `@Transactional` helpers (`createPendingOrder`, `markPaid`, `markFailed`) are
self-invoked, so their annotations have no proxy effect — everything runs in `create()`'s single transaction, which is
rolled back when the payment exception is rethrown (so `FAILED` orders are never actually persisted).

### Cross-cutting coupling to keep in sync

- **JWT contract**: `JWT_ISSUER` and `JWT_AUDIENCE` must be identical in user-service (`app.jwt.*`) and every resource
  server (`spring.security.oauth2.resourceserver.jwt.issuer-uri/audiences`), and tokens carry roles in the `roles` claim
  that `libs/common` maps to `ROLE_*`.
- **Metric names** are referenced in Prometheus form (dots → underscores, `_total` / `_seconds_bucket` suffixes) by
  `deploy/prometheus/alerts.yml` and `deploy/grafana/dashboards/orders-dashboard.json`. Renaming a meter or the
  `paymentClient` Resilience4j instance breaks alerts/dashboards. Alerts also depend on the Prometheus job names
  `api-gateway`, `user-service`, `order-service` in `deploy/prometheus/prometheus.yml` (the `ServiceDown` alert
  matches them by regex).
- **Schema**: each service owns its own database and Flyway migrations (`src/main/resources/db/migration`, naming
  `V1.001__...sql`) in `user_schema` / `order_schema` inside `user_db` / `order_db`. Databases and login roles are
  created by `deploy/postgres/init-databases.sql`, which only runs when the Postgres volume is first created.
  Hibernate runs with `ddl-auto: validate`, so entity changes need a matching new migration.
- **Tests**: each service's `src/test/resources/application.yaml` *shadows* (does not merge with) its main
  `application.yaml`, because they share a file name. It sets H2 in PostgreSQL mode and `ddl-auto: create-drop`, tracing
  sampling 0, and anything else the test context needs (e.g. order-service's `jwk-set-uri`). Order controller tests mock
  `PaymentClient` with `@MockitoBean` because the real one fails 10% of calls at random.

## Local stack wiring

Prometheus (in Docker) scrapes the host services at `host.docker.internal:{8080,8081,8082}/actuator/prometheus`. Each
service exports traces over OTLP HTTP to `http://localhost:4318/v1/traces` (Tempo); override with
`OTEL_EXPORTER_OTLP_ENDPOINT` and
`TRACING_SAMPLING_PROBABILITY` (default 1.0 so every logged `traceId` resolves in Tempo). Grafana (:3000, anonymous
viewer) is provisioned from
`deploy/grafana/provisioning`. Alertmanager's receiver is intentionally empty. Logs are structured ECS JSON (Spring
Boot's built-in
`StructuredLogEncoder`, flat dotted keys; MDC `traceId`/`spanId`/`requestId` become top-level fields) on the console and
in `logs/<spring.application.name>.log` (override dir with `LOG_PATH`; size-rotated).

Log pipeline: Alloy (`deploy/alloy/config.alloy`) tails `./logs/*.log` via a read-only bind mount and pushes to Loki
(`deploy/loki/loki.yaml`, single binary, filesystem, TSDB schema v13, 7-day retention). Indexed labels are only
`service_name`, `environment`, `level`, `source`; `trace_id`/`span_id`/`request_id` are Loki structured metadata, so
query
them as `{service_name="order-service"} | trace_id="..."`, never as stream selectors. Lines that aren't JSON get
`level="unknown"`. Alloy does not log retried push failures — check `loki_write_*` metrics on `localhost:12345/metrics`.
Loki's image is distroless, so it has no Compose healthcheck; use `curl localhost:3100/ready`. Tempo needs the
`tempo-init` service to chown its volume (Tempo runs as UID 10001).
