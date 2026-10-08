# Enterprise SaaS Backend Start-Fresh Plan

> **For Hermes:** Use the `plan` skill outputs and this file as the single execution blueprint. Implement in scoped tasks, one module/migration/test slice at a time. No workflow logic beyond schema/module/auth/org boundary cleanup.

**Goal:** Reset the backend codebase and database to a clean, docs-aligned Enterprise SaaS foundation with target roles `ADMIN`, `ORG_ADMIN`, `INSPECTOR`, `MAINTENANCE_ENGINEER`; remove legacy Provider/Client/marketplace noise; restructure modules so teammates can implement workflows later without untangling old code.

**Master scope clarification:**
- Old code may be deleted when it creates ambiguity or noise.
- Non-workflow infrastructure can be edited to match current docs: auth, roles, access policy, module boundaries, persistence schema, repositories, DTOs, facades only where needed for module shape.
- MF1/MF2/MF3/MF4 business workflow implementation is **not** in scope here.
- The backend must be clean enough for teammates to start fresh on workflows.

**Non-goals:**
- No frontend/mobile implementation.
- No Report 3 / database-design docs rewrite in this pass.
- No online payment gateway.
- No external deployment/commit/merge without explicit authorization.
- No claim that schema-only records mean workflow features are done.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring Modulith 2.1.1, JPA, PostgreSQL, Flyway, MinIO, Testcontainers, JUnit, AssertJ, Spotless, JaCoCo.

**Observed baseline:** branch `main`, head `b00e0b9`, migrations through `V23__provider_onboarding.sql`, current modules `users`, `assets`, `inspectionrequests`, `inspections`, `maintenance`, `notifications`, `shared`, `infrastructure`, `dashboard`.

---

## 1. Architecture reset decision

### 1.1 Target module map

Keep and extend only target modules:

```text
users              auth, organizations, users, user_roles, sessions, refresh tokens
subscriptions      enterprise subscription plan/period/status/entitlement
workforce          inspector/engineer credentials, qualifications, compliance documents
assets             asset catalog, asset documents, drones, pair assignments, checklist templates
inspections        inspection setup snapshots, readiness/session records, evidence, findings, reports
maintenance        work orders, teams, tasks, cost lines, change orders, work logs, reports, acceptance
notifications      notification records and event-facing contracts only
shared             ApiResponse, ProblemDetail, security context contracts, audit base
infrastructure     MinIO/AI/LLM adapters behind feature-owned ports
dashboard          read-only aggregation only; no ownership of business tables
```

Remove or retire:

```text
inspectionrequests legacy Provider quotation/service-order/marketplace module
ProviderRegistrationService, ProviderUserService, ClientRegistrationService paths that map to removed roles
Provider-specific APIs/DTOs/entities/repositories/tests
MF5/provider/direct-transfer/dispute-ticket old runtime assumptions in target code
```

### 1.2 Role contract

Target roles:

```text
ADMIN                     platform owner, organization activation and global support
ORG_ADMIN                 customer organization administrator
INSPECTOR                 field inspector
MAINTENANCE_ENGINEER      maintenance engineer
```

Target actor-zone model:

```text
PLATFORM                  ADMIN only
CUSTOMER_ORGANIZATION     ORG_ADMIN / INSPECTOR / MAINTENANCE_ENGINEER scoped to one organization
```

Do **not** keep legacy `CLIENT`, `PROVIDER_MANAGER`, `PLATFORM_OPERATOR`, `SERVICE_MANAGER`, or six-role terminology in target code or tests. If migration needs source values, preserve them only in a migration adapter or SQL mapping precheck.

### 1.3 Database reset strategy

Because old marketplace code creates too much noise, use a forward migration that introduces the target enterprise schema and freezes legacy tables as deprecated/read-only where needed. Do **not** rewrite applied migrations `V1`–`V23`.

Preferred path:

1. Add target tables as additive `V24__*` / `V25__*` with explicit ownership.
2. Add compatibility views/bridges only when tests still require old shapes.
3. Update code/tests to target roles/modules directly.
4. In later approved slices, drop legacy runtime dependencies and optionally legacy tables.

Populated-data rule: every role rewrite, provider NULL-out, organization assignment, or foreign-key tighten must fail loudly if legacy rows cannot be mapped deterministically. Never invent permissions for old Provider/Client users.

---

## 2. Detailed target tables

Target logical inventory from current docs (41 application tables plus framework tables):

```text
organizations
subscriptions
users
user_roles
auth_sessions
refresh_tokens

workforce_credentials
drones
drone_documents
flight_permits

assets
asset_documents
asset_pair_assignments
inspection_schedules
inspections

inspection_preparations
inspection_readiness_decisions
field_sessions
checklist_responses

evidence
evidence_quality_decisions
ai_finding_candidates
verified_findings
inspection_reports
inspection_report_versions
report_version_evidence
report_version_findings

maintenance_work_orders
maintenance_tasks
maintenance_team_members
maintenance_estimate_versions
maintenance_cost_lines
maintenance_change_orders
maintenance_work_logs
maintenance_report_versions
maintenance_acceptance_decisions

audit_events
notifications
event_publication -- framework only
```

Module ownership:

| Table | Owner module |
|---|---|
| organizations, users, user_roles, auth_sessions, refresh_tokens | users |
| subscriptions | subscriptions |
| workforce_credentials, drones, drone_documents, flight_permits, asset_pair_assignments | workforce/assets split: workforce owns people credentials; assets owns Drone/asset resources if asset is the operational owner, otherwise assets owns asset pair and workforce exposes credential references |
| assets, asset_documents, inspection_schedules, inspections | assets for catalog/resources, inspections for inspection execution metadata if inspection owns lifecycle |
| inspection_preparations, inspection_readiness_decisions, field_sessions, checklist_responses | inspections |
| evidence, evidence_quality_decisions, ai_finding_candidates, verified_findings, inspection_reports, report_version_* | inspections |
| maintenance_* | maintenance |
| audit_events, notifications, event_publication | shared/notifications respectively; event_publication stays framework |

---

## 3. Execution phases

### Phase 0 — Baseline freeze

**Files touched:** no source edits yet.

Tasks:

1. Run `git status --short`, `git branch --show-current`, `git rev-parse HEAD`.
2. Record migration head.
3. Inventory all target-role/auth references and legacy provider references.
4. Build the live-to-target table map.
5. Identify test fixtures that assume six roles or Provider marketplace.

**Verification:** inventory is written into the task handoff or issue comment; no production code changes.

### Phase 1 — Remove ambiguity and establish target grammar

**Status:** in progress; implementer compacted legacy code, reviewer found minor residual grammar.

**Goal:** make the target domain obvious to future developers.

Follow-up tasks from reviewer:

1. Remove dead security rules for deleted provider endpoints in `SecurityConfig.java`.
2. Rename residual legacy vocabulary in target grammar: `CLIENT_SELECTED`, `AWAITING_CLIENT_APPROVAL`, `client_decision_*`, `serviceRole`, `providerId` local names.
3. Keep historical six-role/provider migrations as SQL history but stop JPA code/tests from asserting provider/marketplace target behavior.
4. Run targeted tests plus Modulith boundary verification.

**Verification:** `./mvnw test -Dtest=RolePolicyTest,ModulithArchitectureTest` or broader if shared enums change.

### Phase 2 — Reclassify modules and delete noisy legacy code

**Goal:** make `src/main/java/com/smartdroneinspection` match the target module map.

Tasks:

1. Add package roots:
   - `subscriptions`
   - `workforce`
2. Remove `inspectionrequests` from target code and dependency graph.
3. Remove runtime references to Provider/marketplace services in target modules.
4. Update Modulith `package-info.java` allowedDependencies.
5. Update `ModulithArchitectureTest` for target module list.

**Deletion rule:** delete legacy classes when they only serve Provider/Client/marketplace or six-role authorization; do not keep dead DTO/service/repository classes “for later.”

**Verification:** `ModulithArchitectureTest`, `PersistenceEntityOwnershipTest`, targeted repository/service tests that remain.

### Phase 3 — Full DB schema reset

**Goal:** create the target full database schema in forward migrations without rewriting history.

Tasks:

1. Write `V24__enterprise_saas_target_schema.sql`:
   - create target tables;
   - add constraints, indexes, FK delete behavior;
   - retain only framework dependencies;
   - no provider references in new required constraints;
   - no `peer_reviews` assumptions.
2. Write `V25__legacy_provider_runtime_freeze.sql` if needed:
   - add deprecation comments/views/read-only access;
   - backfill organizations/subscriptions/users/assets/inspections where deterministic;
   - fail loudly on unmapped legacy role or orphan rows.
3. Update entity mappings and repositories to target tables.
4. Update schema tests:
   - `FullDatabaseSchemaMigrationTest`
   - `FullDatabaseSchemaSqlContractTest`
   - `DirectTransferSchemaMigrationTest`
   - `PersistenceEntityOwnershipTest`
   - any `*SchemaMigrationTest` affected by deleted legacy tables.

**Verification:** empty migration plus populated legacy migration; fail-closed checks; exact index/constraint inventory.

### Phase 4 — Persistence slice cleanup

**Goal:** make repositories and DTOs match the target schema.

Tasks:

1. Replace or remove legacy entities/DTOs in `users`, `subscriptions`, `workforce`, `assets`, `inspections`, `maintenance`, `notifications`.
2. Keep DTOs strongly typed only where API contracts remain.
3. Delete legacy DTOs/services that map to removed marketplace paths.
4. Keep feature-owned entities inside their modules.
5. Cross-module references stay scalar UUID; no entity/repository leakage.

**Verification:** compile plus targeted persistence tests.

### Phase 5 — Infrastructure alignment

**Goal:** keep adapters behind ports and match docs.

Tasks:

1. Keep MinIO/AI/LLM adapters in `infrastructure` only.
2. Ensure business modules import only feature-owned ports.
3. Remove provider-specific adapter assumptions from ports.
4. Keep configuration documented and environment-driven.

**Verification:** ArchUnit/Modulith boundary plus adapter config tests.

### Phase 6 — Tests and verification

**Goal:** make the clean slate verifiable.

Required checks:

```bash
cd backend
./mvnw spotless:check
./mvnw test
./mvnw verify
```

Also verify:

- Modulith `verify()` passes.
- Flyway empty migration passes.
- Populated migration from representative legacy fixture passes or fails with exact precheck messages.
- JPA schema validation passes.
- Schema-contract tests match the new target table inventory.
- No tests assert legacy six roles or Provider marketplace as target behavior.

### Phase 7 — Handoff to teammates

**Goal:** give teammates a clean runway.

Deliverables:

- target module tree;
- target table inventory;
- target role/permission policy;
- contracts for subscriptions/workforce/assets/inspections/maintenance;
- a README or migration note in backend if useful;
- no stale marketplace terminology outside migration/history notes.

---

## 4. Deletion candidates

Delete when owned by target scope and no future workflow dependency remains:

```text
backend/src/main/java/com/smartdroneinspection/inspectionrequests/**
backend/src/main/java/com/smartdroneinspection/users/service/ProviderRegistrationService.java
backend/src/main/java/com/smartdroneinspection/users/service/ProviderUserService.java
backend/src/main/java/com/smartdroneinspection/users/service/ClientRegistrationService.java
backend/src/test/java/com/smartdroneinspection/users/ProviderRegistrationServiceTest.java
backend/src/test/java/com/smartdroneinspection/users/ProviderUserServiceTest.java
backend/src/test/java/com/smartdroneinspection/users/ClientRegistrationServiceTest.java
backend/src/test/java/com/smartdroneinspection/users/ClientRegistrationApiIntegrationTest.java
backend/src/test/java/com/smartdroneinspection/inspectionrequests/**
legacy provider DTOs, controllers, repositories, entities and tests
legacy MF5/dispute/provider-runtime adapters once target DB no longer uses them
```

Do **not** delete historical SQL migrations. Preserve them to keep DB provenance and migration history valid.

---

## 5. Success criteria

- Backend target module tree matches current docs.
- No target runtime constructor depends on `inspectionrequests`.
- Target code has only four user roles.
- Target database schema is the Enterprise SaaS target, not marketplace schema.
- Legacy Provider/Client artifacts are either deleted from runtime or isolated in historical migrations/read-only legacy tables.
- Teammates can add workflow code without first deleting a confused provider marketplace layer.
- Tests fail loudly on ambiguous legacy data instead of silently inventing mappings.
- `./mvnw verify` is the final gate before handing implementation back.

---

## 6. Risks and safeguards

| Risk | Guard |
|---|---|
| Blindly deleting legacy code breaks migration provenance | Keep migrations; delete only runtime code and tests after dependency tracing. |
| Legacy role data cannot map safely | Fail migration precheck; do not invent role assignment. |
| Evidence ownership is ambiguous | One owner: inspections owns inspection findings/evidence; maintenance owns repair/report evidence refs or separate evidence metadata with explicit links. |
| Cross-module entity sharing | Keep UUID references/facades only. |
| LLM output trusted as truth | Keep draft records separate from verified findings/report approvals. |
| Workflow code leaking back into DB cleanup pass | Separate branches/commits for schema/module reset vs workflow implementation. |

---

## 7. Approval checkpoint

Master confirmed execution policy:

1. Delete `inspectionrequests` from target runtime.
2. Drop Provider/Client historical tables after mapping is frozen; do not keep marketplace history in runtime DB.
3. Use the ownership split that is cleanest: `workforce` owns credentials and drone/compliance metadata; `assets` owns assets, drone inventory and pair assignments.
4. Remove `users.provider_id` and Provider activation columns from target schema and runtime mappings.
5. Treat all stale Provider/Client/MF5/dispute/direct-transfer code and tests as removable if not needed by migration history.
6. Start fresh for auth/roles/module/DB surface; implement no MF1-MF4 workflow logic in this reset pass.

Recommended bounded-risk interpretation: preserve immutable migration history provenance via existing `V1-V23` files only as deployment history, but remove runtime/code/tests and stop carrying those tables forward in the target full schema.
