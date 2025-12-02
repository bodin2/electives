# Bodindecha 2 Electives API

API for the Bodindecha 2 Electives project. It provides endpoints for managing electives, students, and their selections.

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

| Variable Name              | Description                                              | Default Value                              |
|----------------------------|----------------------------------------------------------|--------------------------------------------|
| `HOST`                     | The host the server binds to                             | `0.0.0.0`                                  |
| `PORT`                     | The port the server listens on                           | `8080`                                     |
| `DB_PATH`                  | The SQLite database file path                            | `data.db`                                  |
| `CORS_HOSTS`               | Comma-separated list of allowed CORS origins             | (None, will throw an exception if not set) |
| `PASETO_PRIVATE_KEY`       | The PASETO private key for authentication                | (None, will throw an exception if not set) |
| `PASETO_PUBLIC_KEY`        | The PASETO public key for authentication                 | (None, will throw an exception if not set) |
| `PASETO_ISSUER`            | The PASETO issuer claim. This is not a security option.  | `electives.bodin2.ac.th`                   |

## Routes

TODO