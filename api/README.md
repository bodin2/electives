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

| Task                                    | Description                                                          |
|-----------------------------------------|----------------------------------------------------------------------|
| `./gradlew test`                        | Run the tests                                                        |
| `./gradlew build`                       | Build everything                                                     |
| `./gradlew buildFatJar`                 | Build an executable JAR of the server with all dependencies included |
| `./gradlew buildImage`                  | Build the docker image to use with the fat JAR                       |
| `./gradlew publishImageToLocalRegistry` | Publish the docker image locally                                     |
| `./gradlew run`                         | Run the server                                                       |
| `./gradlew runDocker`                   | Run using the local docker image                                     |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

## Environment Variables

The server can be configured using the following environment variables:

| Variable Name        | Description                                                                 | Default Value                                                                                                                       |
|----------------------|-----------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `APP_ENV`            | The application environment. Can be `development`, `test`, or `production`. | (Unset, will assume `production`)<br>**Try not to set in production environments.**                                                 |
| `HOST`               | The host the server binds to                                                | `0.0.0.0`                                                                                                                           |
| `PORT`               | The port the server listens on                                              | `8080`                                                                                                                              |
| `DB_PATH`            | The SQLite database file path                                               | `data.db`                                                                                                                           |
| `CORS_HOSTS`         | Comma-separated list of allowed CORS origins                                | (None)<br>Defaults to `*` when `APP_ENV` is `development` or `test`.<br>Otherwise will throw an exception in production if not set. |
| `PASETO_PRIVATE_KEY` | The PASETO private key for authentication                                   | (None, will throw an exception if not set)                                                                                          |
| `PASETO_PUBLIC_KEY`  | The PASETO public key for authentication                                    | (None, will throw an exception if not set)                                                                                          |
| `PASETO_ISSUER`      | The PASETO issuer claim. This is not a security option.                     | `electives.bodin2.ac.th`                                                                                                            |
| `ARGON2_MEMORY`      | The memory cost for Argon2 password hashing                                 | `65536` (64 MB)                                                                                                                     |
| `ARGON2_AVG_TIME`    | The average time cost (in milliseconds) for Argon2 password hashing         | `500` (0.5s)                                                                                                                        |
| Variable Name                                       | Description                                                                 | Default Value                                                                                                                       |
|-----------------------------------------------------|-----------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `APP_ENV`                                           | The application environment. Can be `development`, `test`, or `production`. | (Unset, will assume `production`)<br>**Try not to set in production environments.**                                                 |
| `HOST`                                              | The host the server binds to                                                | `0.0.0.0`                                                                                                                           |
| `PORT`                                              | The port the server listens on                                              | `8080`                                                                                                                              |
| `DB_PATH`                                           | The SQLite database file path                                               | `data.db`                                                                                                                           |
| `CORS_HOSTS`                                        | Comma-separated list of allowed CORS origins                                | (None)<br>Defaults to `*` when `APP_ENV` is `development` or `test`.<br>Otherwise will throw an exception in production if not set. |
| `PASETO_PRIVATE_KEY`                                | The PASETO private key for authentication                                   | (None, will throw an exception if not set)                                                                                          |
| `PASETO_PUBLIC_KEY`                                 | The PASETO public key for authentication                                    | (None, will throw an exception if not set)                                                                                          |
| `PASETO_ISSUER`                                     | The PASETO issuer claim. This is not a security option.                     | `electives.bodin2.ac.th`                                                                                                            |
| `ARGON2_MEMORY`                                     | The memory cost for Argon2 password hashing                                 | `65536` (64 MB)                                                                                                                     |
| `ARGON2_AVG_TIME`                                   | The average time cost (in milliseconds) for Argon2 password hashing         | `500` (0.5s)                                                                                                                        |
| `NOTIFICATIONS_UPDATE_MAX_SUBSCRIPTIONS_PER_CLIENT` | Maximum amount of subjects a client can subscribe to                        | `5`                                                                                                                                 | 
| `NOTIFICATIONS_BULK_UPDATE_INTERVAL`                | Time in milliseconds between each bulk update notifications                 | `5000` (5 seconds)                                                                                                                  |

## Routes

TODO