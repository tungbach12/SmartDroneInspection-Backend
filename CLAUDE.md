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
- Implemented runtime modules: `users`, `assets`, `inspectionrequests`, and `shared`.
- Persistence modules: `inspections` (WF3), `maintenance` (WF4), and `notifications`; their application services and APIs are added incrementally.
- Scaffold-only roots: `dashboard` and `infrastructure` (package metadata unless a real read-model or adapter slice exists).
- `<feature>/api/` - controllers and request/response records
- `<feature>/domain/` - entities and domain rules owned by the feature
- `<feature>/domain/enums/` - domain-only enums; transport enums stay with their API DTOs
- `<feature>/repository/` - persistence repositories
- `<feature>/service/` - application use cases
- `infrastructure/` - scaffolded outbound-adapter boundary; add adapters only with a feature-owned port

Approved capability roots may contain `package-info.java` before runtime code
exists. Do not add empty nested packages or speculative business types; create
them with the first vertical slice that owns real code.

Each direct package under `com.smartdroneinspection` is a Spring Modulith module.
The module root is its default Java API; nested packages are internal unless
exposed with `@NamedInterface`. HTTP `api/` is transport code and must not be
imported by another module. Use a root facade or a named `events`/`spi` interface
for cross-module calls. Business modules never import `infrastructure`.

Dependency direction: `users -> shared::auth, shared::config, shared::exception`, `assets -> shared`,
`inspectionrequests -> assets, shared`, `inspections -> inspectionrequests, assets,
shared`, and `maintenance -> inspections, inspectionrequests, shared`.
Do not create standalone `missions`, `reports`, `defects`, `tickets`, or `ai`
modules; reports/findings/AI candidates belong in WF3, maintenance tickets in
`maintenance`, and YOLO adapters in `infrastructure/ai`.

Do not create a top-level `domain/` entity module. User entities belong in
`com.smartdroneinspection.users.domain`; the domain package is internal to the `users` module.

Modulith boundaries are enforced at build time by `ModulithArchitectureTest`.

## Conventions

- Conventional Commits, squash merge, one reviewer
- DTO records: `XxxRequest` / `XxxResponse`
- Validation: Jakarta Bean Validation (`@Valid`) on request records
- Errors: expected -> `Result<T>`; unexpected -> RFC 7807 `ProblemDetail` via `GlobalExceptionHandler`
- Create or modify Flyway migrations only when the task explicitly includes schema work. Use a new forward migration and never rewrite an applied migration.
- Auth is owned by the `users` module; shared filter-chain configuration remains under `shared/`

## Notes

- The .NET backend is archived on branch `archive/dotnet-final` (local `../backend-backup/`).
- JaCoCo excludes feature `**/domain/**`, `shared/**`, and the application class until feature phases land.
