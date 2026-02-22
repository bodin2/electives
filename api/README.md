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
| `ADMIN_ALLOWED_IPS`                                 | Comma-separated list of CIDR ranges allowed to access admin endpoints                          | `127.0.0.0/8, ::1/128`<br>Setting to an empty value will use the default. To allow any IP, set to `*` **(dangerous!)**.             |
| `ADMIN_PUBLIC_KEY`                                  | A base64-encoded X.509 SubjectPublicKeyInfo RSA public key without PEM headers/footers         | (None)<br>**Required if `ADMIN_ENABLED` is set.**                                                                                   |

### Admin Key Generation

To generate an RSA key pair for admin authentication, you can use the following OpenSSL commands:

```bash
# Generate a 2048-bit RSA private key and save it to private_key.pem
openssl genpkey -algorithm RSA -out private_key.pem -pkeyopt rsa_keygen_bits:2048
# Extract the public key from the private key and save it to public_key.pem
openssl rsa -pubout -in private_key.pem -out public_key.pem
```

#### Windows (PowerShell)

To extract the base64-encoded public key string on Windows using PowerShell, run:

```powershell
# Read the public key file, remove PEM headers/footers, and concatenate the lines
(Get-Content -Raw -Path public_key.pem) -replace '-----BEGIN PUBLIC KEY-----|-----END PUBLIC KEY-----|\s' -join ''
```

#### Unix-like Systems (Linux, macOS)

To extract the base64-encoded public key string on Unix-like systems, run:

```bash
# Read the public key file, remove PEM headers/footers, and concatenate the lines
awk 'NF {sub(/-----BEGIN PUBLIC KEY-----/, ""); sub(/-----END PUBLIC KEY-----/, ""); printf "%s", $0}' public_key.pem
```

---

You can copy the resulting string and set it as the value of the `ADMIN_PUBLIC_KEY` environment variable.

> [!IMPORTANT]  
> **Keep the generated private key (`private_key.pem`) secure**, as it will be used to authenticate and receive admin session tokens.
> 
> If the private key is compromised, a new key pair should be generated immediately, and the `ADMIN_PUBLIC_KEY`
> environment variable should be updated with the new public key.