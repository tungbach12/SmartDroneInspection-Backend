# Backend - SmartDroneInspection

Modular monolith: Spring Boot 4.1, Java 21, Maven wrapper, Spring Modulith, PostgreSQL (pgvector) + MinIO via docker-compose.

## Commands

- `docker compose up -d` - start postgres + MinIO (required before run/tests)
- `./mvnw verify` (`.\mvnw.cmd verify` on Windows) - Spotless check + tests + JaCoCo 80% gate + Modulith boundary check
- `./mvnw spring-boot:run` - run API; Swagger UI at `/swagger-ui.html`, OpenAPI at `/v3/api-docs`
- `mvn spotless:apply` - auto-fix formatting (Google Java Format)

## Structure

- `SmartDroneInspectionApplication` (`@Modulithic`) - entry point
- `shared/` - `Result<T>`, `PagedResult`, `Roles`, RFC 7807 handler, and shared security configuration
- `<feature>/` (assets, inspections, missions, reports, defects, tickets, ai, dashboard, users) - one Spring Modulith module per business capability
- `<feature>/api/` - controllers and request/response records
- `<feature>/domain/` - entities and domain rules owned by the feature
- `<feature>/repository/` - persistence repositories
- `<feature>/service/` - application use cases
- `infrastructure/` - outbound adapters (MinIO, AI, and notification clients)

Do not create a top-level `domain/` entity module. User entities belong in
`com.smartdroneinspection.users.domain`; the domain package is internal to the `users` module.

Modulith boundaries are enforced at build time by `ModulithArchitectureTest`.

## Conventions

- Conventional Commits, squash merge, one reviewer
- DTO records: `XxxRequest` / `XxxResponse`
- Validation: Jakarta Bean Validation (`@Valid`) on request records
- Errors: expected -> `Result<T>`; unexpected -> RFC 7807 `ProblemDetail` via `GlobalExceptionHandler`
- Flyway SQL under `src/main/resources/db/migration` (`V{n}__desc.sql`) when the task includes database schema work; use forward-only migrations.
- Auth is owned by the `users` module; shared filter-chain configuration remains under `shared/`

## Notes

- The .NET backend is archived on branch `archive/dotnet-final` (local `../backend-backup/`).
- JaCoCo excludes feature `**/domain/**`, `shared/**`, and the application class until feature phases land.
