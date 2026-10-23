# Observability

A Spring Boot sample service for creating and retrieving orders, with PostgreSQL persistence and a local Prometheus,
Grafana, Alertmanager, and Tempo stack. The payment client is simulated so order metrics, traces, dashboards, and alerts
can be exercised without an external payment provider.

## Technology

- Java 27 Gradle toolchain; compilation targets Java 21 bytecode (`options.release = 21`) for compatibility with the
  Spring Framework version managed by Spring Boot 3.4.2.
- Spring Boot 3.4.2, Spring Data JPA, Hibernate, and Flyway.
- PostgreSQL 16 for local development.
- Micrometer/Prometheus metrics, OpenTelemetry traces, and Resilience4j retry/circuit-breaker instrumentation.
- Docker Compose provisions the database and observability tools. Run the application separately with Gradle.

## Run locally

Prerequisites: Docker Compose and a JDK 27 installation available to Gradle.

Start PostgreSQL and the observability stack:

```bash
docker compose up -d
```

Run the application in another terminal:

```bash
./gradlew bootRun
```

The app listens on port `8080`. Flyway creates the `app_schema` schema and applies the order-table migration on startup.
The local database uses the `observability` database, username, and password configured in `docker-compose.yml` and
`src/main/resources/application.yaml`.

Open the local tools:

| Service         | URL                     |
|-----------------|-------------------------|
| Grafana         | <http://localhost:3000> |
| Prometheus      | <http://localhost:9090> |
| Alertmanager    | <http://localhost:9093> |
| Tempo query API | <http://localhost:3200> |

Grafana is provisioned with Prometheus and Tempo data sources and an Orders dashboard. Prometheus scrapes the app at
`host.docker.internal:8080`. To stop the services, run `docker compose down`; persistent database and Prometheus/Tempo
data are stored in named Docker volumes.

## Order API

The API is rooted at `/api/v1/orders`. For example, create an order:

```bash
curl -i -X POST http://localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' \
  -d '{
    "customerId": "11111111-1111-1111-1111-111111111111",
    "amount": 42.00
  }'
```

A successful request returns `201 Created` with an order containing its ID, customer ID, amount, status, and creation
time. The payment client simulates a 10% per-attempt decline rate and 20–150 ms of network latency; Resilience4j retries
failed calls up to three attempts and opens the circuit breaker when its configured failure threshold is reached.

Retrieve an order by its UUID:

```bash
curl http://localhost:8080/api/v1/orders/ORDER_ID
```

Replace `ORDER_ID` with the UUID returned when creating an order.

Request validation requires a customer UUID and a positive amount. Errors use a JSON body with `code`, `message`, and
`timestamp`; validation errors return `400`, missing orders return `404`, payment failures return `502`, and an open
payment circuit breaker returns `503`.

## Observability

- Prometheus metrics: <http://localhost:8080/actuator/prometheus>
- Health check: <http://localhost:8080/actuator/health>
- Other exposed Actuator endpoints: `/actuator/info` and `/actuator/metrics`
- Custom order metrics: `orders.created`, `orders.creation.failures`, `orders.payment.success`, and
  `orders.creation.duration`.
- Traces are exported over OTLP HTTP to Tempo at `http://localhost:4318/v1/traces`. Sampling defaults to `0.1`.
- Prometheus alert rules cover app availability, order failure rate, p99 creation latency, and an open payment circuit
  breaker. Alertmanager is included with an intentionally empty receiver; configure a notification integration in
  `deploy/alertmanager/alertmanager.yml` to receive alerts.

The `/api/**` and `/actuator/**` routes are permitted without authentication in this sample so Prometheus can scrape
metrics. Do not expose this configuration to an untrusted network.

## Configuration

The default application configuration is in `src/main/resources/application.yaml`. Tracing can be adjusted with:

```bash
TRACING_SAMPLING_PROBABILITY=0.5 \
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318/v1/traces \
./gradlew bootRun
```

Update the datasource settings in `application.yaml` if using a PostgreSQL instance other than the local Compose
service.

## Tests

Run the test suite with:

```bash
./gradlew test
```

Tests use an in-memory H2 database in PostgreSQL compatibility mode and do not require the Compose services.
