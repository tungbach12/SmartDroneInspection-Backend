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

Each direct package under `com.smartdroneinspection` is a Spring Modulith module.
The package root is the module's default Java API; nested packages are internal
unless explicitly exposed with `@NamedInterface`.

Implemented/runtime modules:

- `users` - identity, authentication, and user administration.
- `assets` - asset catalog, checklist templates, and recurring schedules (WF1).
- `inspectionrequests` - requests, quotations, service orders, and assignments (WF2).
- `shared` - minimal cross-cutting contracts and configuration.

Persistence-backed capability modules (application services and APIs are delivered incrementally):

- `inspections` - field execution, evidence, findings, reports, and peer review (WF3); feature entities and repositories are present.
- `maintenance` - assessment, quotation, execution, change, and resolution (WF4); feature entities and repositories are present.
- `notifications` - supporting delivery capability; notification entity and repository are present.

Scaffold-only module roots:

- `dashboard` - read-only composition.
- `infrastructure` - outbound adapters for feature-owned ports.

The scaffold-only roots currently contain only `package-info.java` so the
capability map is visible to the team. Add nested `api`, `domain`, `repository`,
`service`, `events`, or `spi` packages only with the first vertical slice that
owns real code. Every application table is mapped by an entity/repository in its
owning capability; the Spring Modulith `event_publication` table is framework-owned.

Inside a module, use only the packages the capability needs:

```text
<feature>/
|-- api/             # HTTP controllers and DTOs; internal transport code
|-- domain/          # feature-owned entities and rules; internal
|   `-- enums/       # domain enums; transport enums stay under api/dto
|-- repository/      # scoped persistence; internal
|-- service/         # use-case orchestration; internal
|-- events/          # public only when marked @NamedInterface("events")
`-- spi/             # public only when marked @NamedInterface("spi")
```

Other modules must not import another module's controllers, HTTP DTOs, entities,
repositories, or services. Use a facade in the module root or a named interface.
Keep entities inside the module that owns their use cases; do not create a global
`com.smartdroneinspection.domain` entity module.

The recommended dependency direction is `users -> shared::auth, shared::config, shared::exception`, `assets -> shared`,
`inspectionrequests -> assets, shared`, `inspections -> inspectionrequests, assets,
shared`, and `maintenance -> inspections, inspectionrequests, shared`.
`infrastructure` implements feature-owned SPI and shared adapters; business modules
never import infrastructure. Reports, findings, and AI candidates belong inside
WF3, maintenance tickets belong inside `maintenance`, and a YOLO client belongs
under `infrastructure/ai` when that runtime slice is implemented. Do not create
standalone `missions`, `reports`, `defects`, `tickets`, or `ai` modules.

## Conventions

- Conventional Commits, squash merge, and one reviewer.
- DTO records use `XxxRequest` and `XxxResponse` names.
- Validate request records with Jakarta Bean Validation.
- Expected business failures use `Result<T>`; unexpected failures use RFC 7807 `ProblemDetail`.
- Create or modify Flyway migrations only when the task explicitly includes schema work. Use a new forward migration and never rewrite an applied migration.
- Approved capability roots may contain `package-info.java` before runtime code exists. Do not add empty nested packages or speculative business types.

## Authentication

Authentication is owned by the `users` module. Browser auth uses a memory-only access token,
CSRF protection, and an HttpOnly refresh cookie. Mobile auth uses the mobile contract and
secure client storage. Shared security configuration remains in `shared/`.

## Formatting and coverage

- `mvn spotless:apply` formats Java and configuration files.
- JaCoCo enforces an 80 percent line/instruction gate on `verify`.
- Modulith boundaries are verified by `ModulithArchitectureTest`.

The previous .NET backend is archived on `archive/dotnet-final` and in `../backend-backup/`.
