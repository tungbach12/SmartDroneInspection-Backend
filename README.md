# SmartDroneInspection - Backend

Modular monolith built with Spring Boot 4.1, Java 21, Spring Modulith, PostgreSQL (pgvector), and MinIO.

## Quickstart

```bash
# Start PostgreSQL (pgvector) and MinIO
docker compose up -d

# Verify formatting, tests, coverage, and Modulith boundaries
./mvnw verify
# Windows: .\mvnw.cmd verify

# Run the API
./mvnw spring-boot:run
```

Swagger UI is available at `/swagger-ui.html` in development. OpenAPI is available at `/v3/api-docs`.

## Package structure

```text
com.smartdroneinspection
├── SmartDroneInspectionApplication   # @Modulithic entry point
├── shared/                            # Cross-cutting types and security config
├── users/                             # Authentication and user management module
│   ├── api/                           # Controllers and public API entry points
│   │   └── dto/
│   │       ├── request/               # Feature-owned request records
│   │       └── response/              # Feature-owned response records
│   ├── domain/                        # User entities and domain rules
│   ├── repository/                    # Persistence repositories
│   ├── security/                      # JWT, password, crypto, and rate limit code
│   └── service/                       # Authentication and user use cases
├── <feature>/                         # Other business modules (assets, reports, etc.)
│   ├── api/
│   ├── domain/
│   ├── repository/
│   └── service/
└── infrastructure/                    # MinIO, AI, and notification adapters
```

Each direct package under `com.smartdroneinspection` is a Spring Modulith module.
Keep entities inside the module that owns their use cases, for example
`com.smartdroneinspection.users.domain.User`. Do not create a global
`com.smartdroneinspection.domain` entity module; it separates entities from their
application module and weakens the intended boundary.

## Conventions

- Conventional Commits, squash merge, and one reviewer.
- DTO records use `XxxRequest` and `XxxResponse` names.
- Validate request records with Jakarta Bean Validation.
- Expected business failures use `Result<T>`; unexpected failures use RFC 7807 `ProblemDetail`.
- Flyway migrations live under `src/main/resources/db/migration` and are managed sequentially by the team leader.

## Authentication

Authentication is owned by the `users` module. Browser auth uses a memory-only access token,
CSRF protection, and an HttpOnly refresh cookie. Mobile auth uses the mobile contract and
secure client storage. Shared security configuration remains in `shared/`.

## Formatting and coverage

- `mvn spotless:apply` formats Java and configuration files.
- JaCoCo enforces an 80 percent line/instruction gate on `verify`.
- Modulith boundaries are verified by `ModulithArchitectureTest`.

The previous .NET backend is archived on `archive/dotnet-final` and in `../backend-backup/`.
