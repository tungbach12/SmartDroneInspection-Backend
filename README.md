# SmartDroneInspection — Backend (Java / Spring Boot)

Modular monolith · Spring Boot 4.1 · Java 21 · Maven wrapper · Spring Modulith · PostgreSQL (pgvector) · MinIO

## Quickstart

```bash
# 0. Start infra (from backend/)
docker compose up -d        # postgres (pgvector:pg17) + MinIO (S3)

# 1. Verify (Spotless + modulith boundary check + integration tests + JaCoCo 80%)
./mvnw verify               # Windows: ./mvnw.cmd verify

# 2. Run
./mvnw spring-boot:run
# Swagger UI:  http://localhost:8080/swagger-ui.html
# OpenAPI:     http://localhost:8080/v3/api-docs
```

No local Maven install needed — `./mvnw` downloads the right version. Docker must be running for integration tests.

## Layout

```
com.smartdroneinspection
├── SmartDroneInspectionApplication        (@Modulithic — entry point)
├── domain/               ← ALL entities live here (like MinimalClean.Domain/)
│   ├── ai/ assets/ common/ defects/ missions/ planning/ reports/ tickets/ users/
├── shared/               ← Result<T>, PagedResult, Roles, ProblemDetail, SecurityConfig
├── <feature>/            ← one package per capability (vertical slices)
│   assets/ inspections/ missions/ reports/ defects/ tickets/ ai/ dashboard/ users/
│   (controller + service + repo + request/response records land here, Phase 5+)
└── infrastructure/       ← outbound adapters (SmartDroneHub REST, MinIO, LLM clients)
```

Modulith boundaries are enforced at build time by `ModulithArchitectureTest` — a feature
touching another feature's internals fails the build.

## Conventions

- ConvCommits (`feat(assets): ...`), squash-merge, 1 reviewer
- DTO records: `XxxRequest` / `XxxResponse`
- Validation: Jakarta Bean Validation (`@Valid`) on request records
- Errors: expected → `Result<T>`; unexpected → RFC 7807 `ProblemDetail` via `GlobalExceptionHandler`
- Flyway SQL under `src/main/resources/db/migration` (team leader only, sequential `V{n}__desc.sql`)

## Auth (skeleton)

Current `SecurityConfig` permits `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health/**` and requires auth elsewhere. JWT wiring lands with the `users/` feature.

## Formatting / coverage

- Spotless (Google Java Format) runs on `validate` — `mvn spotless:apply` to auto-fix.
- JaCoCo coverage gate (80 % line/instruction) on `verify` — report at `target/site/jacoco/`.

## Restore .NET era

The .NET backend is preserved on GitHub branch `archive/dotnet-final` and in the local clone
`../backend-backup/`.
