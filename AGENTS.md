# Backend - SmartDroneInspection

Modular monolith: Spring Boot 4.1, Java 21, Maven wrapper, Spring Modulith, PostgreSQL (pgvector) + MinIO via docker-compose.

## Documentation scope

Create or update documentation only when the user explicitly requests it. Consult or cite documents only when they help complete or verify the task. Use the current code, API contracts, and tests when they provide the needed evidence.

## Commands

- `docker compose up -d` - start postgres + MinIO (required before run/tests)
- `./mvnw verify` (`.\mvnw.cmd verify` on Windows) - Spotless check + tests + JaCoCo 80% gate + Modulith boundary check
- `./mvnw spring-boot:run` - run API; Swagger UI at `/swagger-ui.html`, OpenAPI at `/v3/api-docs`
- `mvn spotless:apply` - auto-fix formatting (Google Java Format)

## Structure

- `SmartDroneInspectionApplication` (`@Modulithic`) - entry point
- `shared/` - `ApiResponse<T>`, `Result<T>`, `PagedResult`, `Roles`, RFC 9457 handler, and shared security configuration
- Implemented runtime modules: `users`, `assets`, `contracts`, `inspections`, `notifications`, `dashboard`, and `shared`.
- `infrastructure` contains the concrete MinIO evidence-storage, email/OTP, payment gateway (Sepay / VNPay), SmartDroneHub, and configured YOLO / LLM adapters behind feature-owned ports/`spi` boundaries; business modules must not import infrastructure.
- `<feature>/api/` - controllers and request/response records
- `<feature>/domain/` - entities and domain rules owned by the feature
- `<feature>/domain/enums/` - domain-only enums; transport enums stay with their API DTOs
- `<feature>/repository/` - persistence repositories
- `<feature>/service/` - application use cases
- `infrastructure/` - outbound adapters behind feature-owned ports; keep business behavior and contracts in their owning modules

Approved capability roots may contain `package-info.java` before runtime code
exists. Do not add empty nested packages or speculative business types; create
them with the first vertical slice that owns real code.

Each direct package under `com.smartdroneinspection` is a Spring Modulith module.
The module root is its default Java API; nested packages are internal unless
exposed with `@NamedInterface`. HTTP `api/` is transport code and must not be
imported by another module. Use a root facade or a named `events`/`spi` interface
for cross-module calls. Business modules never import `infrastructure`.

Dependency direction: `users -> shared::api, shared::auth, shared::config, shared::exception, shared::storage`, `assets -> shared`, `contracts -> assets::domain, assets::enums, assets::repository, users, inspections::spi, shared`, `inspections -> assets::domain, assets::enums, assets::repository, assets::reportcontext, users, shared`. Do not create standalone `missions`, `reports`, `defects`, `tickets`, or `ai` modules; reports/findings/AI candidates belong in `inspections`, and adapters in `infrastructure`.

Do not create a top-level `domain/` entity module. User entities belong in
`com.smartdroneinspection.users.domain`; the domain package is internal to the `users` module.

Modulith boundaries are enforced at build time by `ModulithArchitectureTest`.

## Conventions

- Conventional Commits, squash merge, one reviewer
- DTO records: `XxxRequest` / `XxxResponse`
- Validation: Jakarta Bean Validation (`@Valid`) on request records
- Successful JSON bodies use `ApiResponse<T>`; preserve `204 No Content` and binary streaming responses. Errors use RFC 9457 `ProblemDetail` via `GlobalExceptionHandler` and the shared security handlers.
- Create or modify Flyway migrations only when the task explicitly includes schema work. Use a new forward migration and never rewrite an applied migration.
- Auth is owned by the `users` module; shared filter-chain configuration remains under `shared/`
- Customer onboarding is the self-registration path: it creates a customer registration review (`PENDING` / `APPROVED` on clear airspace) for a single `CUSTOMER` account (`ownerCustomerId = user.id`, no organization hierarchy). `ADMIN`, `MANAGER`, and `INSPECTOR` accounts are managed/approved via platform governance (`ADMIN` / `MANAGER`). The four login roles are strictly `ADMIN`, `MANAGER`, `CUSTOMER`, `INSPECTOR`.

## Notes

- The .NET backend is archived on branch `archive/dotnet-final` (local `../backend-backup/`).
- JaCoCo excludes feature `**/domain/**`, `shared/**`, and the application class until feature phases land.
