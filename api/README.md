# Bodindecha 2 Electives API

API for the Bodindecha 2 Electives project. It provides endpoints for managing electives, students, and their
selections.

## Technologies Used

- Ktor
- Protocol Buffers

- JetBrains Exposed
- HikariCP
- PostgreSQL (production), SQLite in-memory (tests)

- Kotlin

- OpenTelemetry (traces + logs)
- Micrometer (metrics)

## Building & Running

This project requires Java 25 or higher and Gradle 9.5.0 or higher. The Gradle wrapper is included in the project.
Older versions of Java or Gradle may work but aren't actively tested.

To build or run the project, use one of the following tasks:

| Task                                         | Description                                                          |
| -------------------------------------------- | -------------------------------------------------------------------- |
| `./gradlew :api:test`                        | Run the tests                                                        |
| `./gradlew :api:build`                       | Build everything                                                     |
| `./gradlew :api:buildFatJar`                 | Build an executable JAR of the server with all dependencies included |
| `./gradlew :api:buildImage`                  | Build the docker image to use with the fat JAR                       |
| `./gradlew :api:publishImageToLocalRegistry` | Publish the docker image locally                                     |
| `./gradlew :api:run`                         | Run the server                                                       |
| `./gradlew :api:runDocker`                   | Run using the local docker image                                     |

If the server starts successfully, you'll see the following output:

```
2026-01-01 08:30:00.007 [main] INFO  io.ktor.server.Application - Application started in 7.256 seconds.
2026-01-01 08:30:00.067 [DefaultDispatcher-worker-4] INFO  io.ktor.server.Application - Responding at http://127.0.0.1:8080
```

## Environment Variables

The server can be configured using the following environment variables:

| Variable Name                                       | Description                                                                                                                 | Default Value                                                                                                                                                                                                              |
| --------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `APP_ENV`                                           | The application environment. Can be `development`, `test`, or `production`.                                                 | (Unset, will assume `production`)<br>**Try not to set in production environments.**                                                                                                                                        |
| `HOST`                                              | The host the server binds to                                                                                                | `0.0.0.0`                                                                                                                                                                                                                  |
| `PORT`                                              | The port the server listens on                                                                                              | `8080`                                                                                                                                                                                                                     |
| `DB_URL`                                            | JDBC URL of the PostgreSQL database (e.g. `jdbc:postgresql://host:5432/db?tcpKeepAlive=true`)                               | (None)<br>An error will be thrown if not set.<br>You should also use the `tcpKeepAlive=true` parameter to [maintain connections](https://www.cybertec-postgresql.com/en/tcp-keepalive-for-a-better-postgresql-experience). |
| `DB_USER`                                           | Database username                                                                                                           | (None)                                                                                                                                                                                                                     |
| `DB_PASSWORD`                                       | Database password                                                                                                           | (None)                                                                                                                                                                                                                     |
| `DB_POOL_SIZE`                                      | Maximum number of connections in the HikariCP pool. A common starting point is `(2 × DB cores) + effective_spindles`.       | `10`                                                                                                                                                                                                                       |
| `DB_MINIMUM_IDLE`                                   | Minimum number of idle connections HikariCP keeps. Defaults to `DB_POOL_SIZE` (fixed-size pool) so the full pool is warm.   | `DB_POOL_SIZE`                                                                                                                                                                                                             |
| `DB_CONNECTION_TIMEOUT`                             | Max milliseconds a caller waits for a pooled connection before failing.                                                     | `10000` (10 seconds)                                                                                                                                                                                                       |
| `DB_MAX_LIFETIME`                                   | Max milliseconds a connection lives before being retired and replaced. Should be shorter than any DB/network idle timeout.  | `1800000` (30 minutes)                                                                                                                                                                                                     |
| `DB_IDLE_TIMEOUT`                                   | Max milliseconds an idle connection sits in the pool before removal (only applies when `DB_MINIMUM_IDLE` < `DB_POOL_SIZE`). | `600000` (10 minutes)                                                                                                                                                                                                      |
| `DB_LEAK_DETECTION_THRESHOLD`                       | Milliseconds a connection may be held before HikariCP logs a possible leak. `0` disables detection.                         | `0` (disabled)                                                                                                                                                                                                             |
| `CORS_HOSTS`                                        | Comma-separated list of allowed CORS origins                                                                                | (None)<br>Defaults to `*` when `APP_ENV` is `development` or `test`.<br>Otherwise will throw an exception in production if not set.                                                                                        |
| `ARGON2_MEMORY`                                     | The memory cost for Argon2 password hashing                                                                                 | `65536` (64 MB)                                                                                                                                                                                                            |
| `ARGON2_AVG_TIME`                                   | The average time cost (in milliseconds) for Argon2 password hashing                                                         | `500` (0.5 seconds)                                                                                                                                                                                                        |
| `USER_SESSION_DURATION`                             | Duration of user sessions in seconds                                                                                        | `86400` (24 hours)                                                                                                                                                                                                         |
| `USER_SESSION_CREATION_MINIMUM_TIME`                | Minimum time in milliseconds for creating new user sessions. Prevents spam and timing attacks.                              | `500` (0.5 seconds)                                                                                                                                                                                                        |
| `NOTIFICATIONS_UPDATE_MAX_SUBSCRIPTIONS_PER_CLIENT` | Maximum amount of subjects a client can subscribe to                                                                        | `5`                                                                                                                                                                                                                        |
| `NOTIFICAIONS_BULK_UPDATE_INTERVAL`                 | Time in milliseconds between each bulk update notifications                                                                 | `5000` (5 seconds)                                                                                                                                                                                                         |
| `IS_BEHIND_PROXY`                                   | Set to non-empty value if running behind a proxy (eg. load balancer)                                                        | (None)                                                                                                                                                                                                                     |
| `ADMIN_ENABLED`                                     | Set to non-empty value to enable admin endpoints                                                                            | (None)<br>**Disabled by default for safety.**                                                                                                                                                                              |
| `ADMIN_SESSION_DURATION`                            | Duration of admin sessions in seconds.                                                                                      | `3600` (1 hour)                                                                                                                                                                                                            |
| `ADMIN_SESSION_CREATION_MINIMUM_TIME`               | Minimum time in milliseconds for creating new admin sessions. Prevents spam and timing attacks.                             | `3000` (3 seconds)                                                                                                                                                                                                         |
| `ADMIN_RESET`                                       | See the [Provisioning the Default Admin](#provisioning-the-default-admin) section.                                          | (None)                                                                                                                                                                                                                     |
| `OTEL_SERVICE_NAME`                                 | The `service.name` reported to the telemetry backend.                                                                       | `electives-api`                                                                                                                                                                                                            |
| `OTEL_EXPORTER_OTLP_ENDPOINT`                       | Base OTLP endpoint for traces, metrics, and logs. For Grafana LGTM, use the HTTP receiver (port `4318`). Unset to disable.  | (None)<br>Disabled by default.                                                                                                                                                                                             |
| `OTEL_EXPORTER_OTLP_HEADERS`                        | Extra OTLP headers in `key=value,key2=value2` form. Used for auth against hosted backends (e.g. Grafana Cloud).             | (None)                                                                                                                                                                                                                     |
| `OTEL_METRIC_EXPORT_INTERVAL`                       | How often in milliseconds to let Micrometer push metrics over OTLP.                                                         | `15000` (15 seconds)<br>This is a burst heavy application, so frequent metric pushes are expected.                                                                                                                         |

## Admin Authentication

Admin users authenticate with the standard `POST /auth` endpoint using their user ID and password, exactly like students and teachers.
A successful login returns a session token, send `HEAD /admin` with the token to verify if the session is valid.

To enable the admin endpoints, set the `ADMIN_ENABLED` environment variable to a non-empty value.
While enabled, admin sessions use `ADMIN_SESSION_DURATION` and `ADMIN_SESSION_CREATION_MINIMUM_TIME` instead of the regular `USER_SESSION_*` values.

### Provisioning the Default Admin

To create (or reset) the default admin user, set `ADMIN_RESET` to the desired password and start the server with `ADMIN_ENABLED` set.
On startup the server deletes user `0` if it already exists and recreates it as an admin with the supplied password. **The password must be at least 4 characters once trimmed.**

> [!IMPORTANT]
> You must **unset `ADMIN_RESET` after a successful startup.**
> Leaving it set will cause the default admin user to be deleted and recreated on every restart, invalidating any sessions.

## Observability

The server emits three telemetry signals to the configured OTLP endpoint:

- **Metrics**: HTTP metrics + JVM/system metrics (memory, GC, threads, CPU, file descriptors).
- **Traces**: One span per request. DB access is traced by a `db.query` span per unit of work, with a child `db.statement` span per SQL statement.
- **Logs**: a Logback appender bridges application logs, correlated with traces via `trace_id` and `span_id`.

When no OTLP endpoint is specified or during tests, telemetry is automatically disabled.

### Running a local Grafana LGTM backend

The `grafana/otel-lgtm` all-in-one image bundles Grafana, Tempo, Loki, and Mimir with an OTLP receiver.

Start it with Docker:

```bash
docker run -d --name grafana-lgtm \
  -p 4317:4317 \
  -p 4318:4318 \
  -p 3001:3000 \
  -e GF_SECURITY_ADMIN_USER=admin \
  -e GF_SECURITY_ADMIN_PASSWORD=admin \
  grafana/otel-lgtm:latest
```

Ports:

- `4317` = OTLP gRPC
- `4318` = OTLP HTTP (used by the defaults above)
- `3001` = Grafana UI

Then run the server with the defaults from `.env.example` and open Grafana at <http://localhost:3001> (login `admin`/`admin`).

Traces are under **Drilldown** > **Traces**. Metrics and logs are explorable via their respective data sources.

### Using a hosted backend (e.g. Grafana Cloud)

Point the endpoint at your tenant's OTLP gateway and supply auth headers:

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=https://otlp-gateway-<region>.grafana.net/otlp
OTEL_EXPORTER_OTLP_HEADERS="Authorization=Basic <base64(instanceId:token)>"
```

The transport protocol (`http/protobuf`) and exporter selection (traces/logs over OTLP, metrics by Micrometer) are fixed in `TelemetryService` and need no configuration.
The same headers are forwarded by the Micrometer OTLP registry for metric pushes.

### Profiling

The span waterfall shows which (route, SQL statement, etc.) is slow, but not what is slow.

For per-method CPU flamegraphs, either attach the [OpenTelemetry Java agent](https://opentelemetry.io/docs/zero-code/java/agent/) (auto-instruments JDBC/HikariCP/coroutines with no code changes) or run [Grafana Pyroscope](https://grafana.com/oss/pyroscope/) for continuous profiling. Both are optional and independent of the LGTM setup above.
