# Bodindecha 2 Electives API

API for the Bodindecha 2 Electives project. It provides endpoints for managing electives, students, and their
selections.

## Technologies Used

- Ktor
- Protocol Buffers

- JetBrains Exposed
- SQLite

- Kotlin

## Building & Running

To build or run the project, use one of the following tasks:

| Task                                         | Description                                                          |
|----------------------------------------------|----------------------------------------------------------------------|
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

| Variable Name                                       | Description                                                                                    | Default Value                                                                                                                       |
|-----------------------------------------------------|------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `APP_ENV`                                           | The application environment. Can be `development`, `test`, or `production`.                    | (Unset, will assume `production`)<br>**Try not to set in production environments.**                                                 |
| `HOST`                                              | The host the server binds to                                                                   | `0.0.0.0`                                                                                                                           |
| `PORT`                                              | The port the server listens on                                                                 | `8080`                                                                                                                              |
| `DB_PATH`                                           | The SQLite database file path                                                                  | (None)<br>An error will be thrown if not set.                                                                                       |
| `CORS_HOSTS`                                        | Comma-separated list of allowed CORS origins                                                   | (None)<br>Defaults to `*` when `APP_ENV` is `development` or `test`.<br>Otherwise will throw an exception in production if not set. |
| `ARGON2_MEMORY`                                     | The memory cost for Argon2 password hashing                                                    | `65536` (64 MB)                                                                                                                     |
| `ARGON2_AVG_TIME`                                   | The average time cost (in milliseconds) for Argon2 password hashing                            | `500` (0.5 seconds)                                                                                                                 |
| `USER_SESSION_DURATION`                             | Duration of user sessions in seconds                                                           | `86400` (24 hours)                                                                                                                  |
| `USER_SESSION_CREATION_MINIMUM_TIME`                | Minimum time in milliseconds for creating new user sessions. Prevents spam and timing attacks. | `500` (0.5 seconds)                                                                                                                 |
| `NOTIFICATIONS_UPDATE_MAX_SUBSCRIPTIONS_PER_CLIENT` | Maximum amount of subjects a client can subscribe to                                           | `5`                                                                                                                                 |
| `NOTIFICATIONS_BULK_UPDATE_INTERVAL`                | Time in milliseconds between each bulk update notifications                                    | `5000` (5 seconds)                                                                                                                  |
| `IS_BEHIND_PROXY`                                   | Set to non-empty value if running behind a proxy (eg. load balancer)                           | (None)                                                                                                                              |
| `ADMIN_ENABLED`                                     | Set to non-empty value to enable dangerous admin endpoints for maintenance                     | (None)<br>**Disabled by default for safety.**                                                                                       |
| `ADMIN_ALLOWED_IPS`                                 | Comma-separated list of IPs allowed to access admin endpoints                                  | `127.0.0.1`                                                                                                                         |
| `ADMIN_PUBLIC_KEY`                                  | An RSA SSH public key used to authenticate admin users                                         | (None)<br>**Required if `ADMIN_ENABLED` is set.**                                                                                   |