# Observability

A small microservice system (API gateway, user service, order service) built to exercise a local observability stack:
Prometheus metrics, OpenTelemetry traces to Tempo, logs through Alloy to Loki, Grafana dashboards, and Alertmanager
alerts. Payment is simulated so failures, retries, and circuit-breaker trips actually happen.

## Architecture

```
                         ┌────────────────────┐
  client ──────────────► │ api-gateway :8080  │  routing, X-Request-Id, timeouts
                         └─────────┬──────────┘
              /api/v1/auth, /users │            │ /api/v1/orders
                         ┌─────────▼────────┐  ┌▼──────────────────┐
                         │ user-service     │  │ order-service     │
                         │ :8081            │  │ :8082             │
                         │ register, login, │  │ create/get/list/  │
                         │ issues JWTs      │  │ update own orders │
                         └───┬──────────┬───┘  └───┬───────────────┘
                   user_db ◄─┘          │          └─► order_db
                                        │ GET /.well-known/jwks.json (public keys)
                                        └───────────► verified locally by order-service
```

| Module                   | Purpose                                                                                                                                                                 |
|--------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `services/api-gateway`   | Spring Cloud Gateway (reactive). Single entry point; routes by path.                                                                                                    |
| `services/user-service`  | Registration, login, user profile. The only service that issues tokens.                                                                                                 |
| `services/order-service` | Orders and the simulated payment client. Trusts tokens, never calls the user service.                                                                                   |
| `libs/common`            | Servlet-service platform code, wired by auto-configuration: request logging filter, RFC 9457 error handling, stateless JWT resource-server security. No business logic. |

Design decisions:

- **Database per service.** `user_db` and `order_db`, each with its own login role
  (`deploy/postgres/init-databases.sql`).
  No service reads another's tables.
- **Stateless auth with asymmetric JWTs.** `POST /api/v1/auth/login` returns an RS256 access token. Other services
  verify
  signature, issuer, audience, and expiry against the user service's JWKS (cached, fetched lazily), so there is no
  per-request call between services and the user service can be down without breaking order reads/writes for users
  who already hold a token.
- **The acting user always comes from the token subject**, never from a request body. Orders are scoped to their owner;
  another user's order is reported as `404`, not `403`, so IDs can't be probed.
- **Zero trust behind the gateway.** Every service enforces authentication itself; the gateway only routes.
- **Errors are RFC 9457 `application/problem+json`** with extension members `code` (stable, machine-readable) and
  `timestamp`; validation errors add an `errors` list of `{field, message}`.

## Technology

- Java 27 Gradle toolchain; compilation targets Java 21 bytecode (`options.release = 21`) for compatibility with the
  Spring Framework version managed by Spring Boot 3.4.2.
- Spring Boot 3.4.2, Spring Cloud 2024.0.0 (gateway), Spring Security OAuth2 resource server, Spring Data JPA, Flyway.
- PostgreSQL 16, Micrometer/Prometheus, OpenTelemetry, Resilience4j.

## Run locally

Prerequisites: Docker Compose and a JDK 27 installation available to Gradle.

```bash
docker compose up -d                      # Postgres + observability stack
./gradlew :services:user-service:bootRun  # :8081
./gradlew :services:order-service:bootRun # :8082   (separate terminals)
./gradlew :services:api-gateway:bootRun   # :8080
```

> **Upgrading from the single-app layout?** Postgres only runs its init script when the volume is created. Run
> `docker compose down -v` once so `user_db` and `order_db` get created (this deletes old local data).

With no JWT keys configured, the user service generates an ephemeral RSA key pair at startup (logged as a warning), so
tokens stop working after it restarts. For stable keys, set `JWT_PRIVATE_KEY` (PKCS#8 PEM) and `JWT_PUBLIC_KEY`
(X.509 PEM).

| Service         | URL                     |
|-----------------|-------------------------|
| Grafana         | <http://localhost:3000> |
| Prometheus      | <http://localhost:9090> |
| Alertmanager    | <http://localhost:9093> |
| Tempo query API | <http://localhost:3200> |

## API

All client calls go through the gateway on `:8080`. Base path `/api/v1`.

| Method  | Path           | Auth   | Description                                                                           |
|---------|----------------|--------|---------------------------------------------------------------------------------------|
| `POST`  | `/users`       | public | Register. `201` + `Location`; `409` if the email is taken.                            |
| `POST`  | `/auth/login`  | public | Exchange email and password for a bearer token.                                       |
| `GET`   | `/users/me`    | bearer | The caller's profile.                                                                 |
| `POST`  | `/orders`      | bearer | Create an order for the caller. `201` + `Location`.                                   |
| `GET`   | `/orders`      | bearer | The caller's orders, newest first. `?page=0&size=20` (max 100).                       |
| `GET`   | `/orders/{id}` | bearer | One of the caller's orders.                                                           |
| `PATCH` | `/orders/{id}` | bearer | Update an order. Currently `{"status": "CANCELLED"}`; `409` if it can't be cancelled. |

```bash
# register, log in, create an order
curl -s -X POST localhost:8080/api/v1/users -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.com","password":"correct-horse-battery","displayName":"Ada"}'

TOKEN=$(curl -s -X POST localhost:8080/api/v1/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ada@example.com","password":"correct-horse-battery"}' | jq -r .access_token)

curl -i -X POST localhost:8080/api/v1/orders -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"amount": 42.00}'

curl -X PATCH localhost:8080/api/v1/orders/ORDER_ID -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"status": "CANCELLED"}'
```

Status codes: validation `400`, missing/invalid token `401` (with `WWW-Authenticate`), unknown or foreign order `404`,
conflicts `409`, payment failure `502`, open payment circuit breaker `503`. The payment client simulates a 10%
per-attempt decline rate and 20–150 ms latency; Resilience4j retries up to three attempts and opens the circuit
breaker at its failure threshold.

## Observability

Each service exposes `/actuator/health` and `/actuator/prometheus`, exports traces over OTLP HTTP to Tempo
(`http://localhost:4318/v1/traces`, sampling default `1.0`), and writes ECS JSON logs to `logs/<service-name>.log`,
which
Alloy ships to Loki. A request carries one `X-Request-Id` and one trace across gateway and services.

- Prometheus jobs: `api-gateway`, `user-service`, `order-service`.
- Custom metrics: order service `orders.created`, `orders.creation.failures`, `orders.payment.success`,
  `orders.creation.duration`; user service `users.registered`, `users.logins{outcome}`.
- Alerts: `ServiceDown`, `HighLoginFailureRate`, `HighOrderFailureRate`, `HighOrderCreationLatencyP99`,
  `PaymentCircuitBreakerOpen`. Alertmanager's receiver is intentionally empty; configure one in
  `deploy/alertmanager/alertmanager.yml`.

`/actuator/**` is unauthenticated so Prometheus can scrape it. Do not expose these ports to an untrusted network;
in a real deployment, move management endpoints to a separate internal port.

## Configuration

Each service has its own `src/main/resources/application.yaml`. Useful overrides:

| Variable                                                                  | Service     | Default                                                    |
|---------------------------------------------------------------------------|-------------|------------------------------------------------------------|
| `DB_USERNAME` / `DB_PASSWORD`                                             | user, order | `user_service` / `order_service` (same as name)            |
| `JWT_PRIVATE_KEY` / `JWT_PUBLIC_KEY`                                      | user        | empty → ephemeral dev keys                                 |
| `JWT_ISSUER` / `JWT_AUDIENCE`                                             | user, order | `http://localhost:8081` / `observability-api` (must match) |
| `USER_SERVICE_JWKS_URI`                                                   | order       | `http://localhost:8081/.well-known/jwks.json`              |
| `USER_SERVICE_URL` / `ORDER_SERVICE_URL`                                  | gateway     | `http://localhost:8081` / `:8082`                          |
| `TRACING_SAMPLING_PROBABILITY`, `OTEL_EXPORTER_OTLP_ENDPOINT`, `LOG_PATH` | all         | `1.0`, Tempo on localhost, `logs`                          |

## Tests

```bash
./gradlew test
```

Tests use in-memory H2 in PostgreSQL mode and need no Compose services. A test `application.yaml` replaces the main one
entirely, so settings a test needs must be repeated there.

## Not included (typical next steps)

Refresh tokens and logout/revocation, login rate limiting or lockout, service discovery (routes are static URLs),
per-service container images and Compose entries, an `Idempotency-Key` for order creation, and event-driven integration
(outbox + broker) if services ever need to react to each other's changes.
