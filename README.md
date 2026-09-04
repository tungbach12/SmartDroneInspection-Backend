# SmartDroneInspection Backend API

[![.NET](https://img.shields.io/badge/.NET-9%20%2F%2010-purple.svg)](https://dotnet.microsoft.com/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17%20%2B%20pgvector-blue.svg)](https://github.com/pgvector/pgvector)
[![FastEndpoints](https://img.shields.io/badge/FastEndpoints-REPR%20Pattern-brightgreen.svg)](https://fast-endpoints.com/)
[![Architecture](https://img.shields.io/badge/Architecture-Ardalis%20Clean%20%2F%20Vertical%20Slice-orange.svg)](https://github.com/ardalis/CleanArchitecture)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**SmartDroneInspection** (Capstone FA26SE112 — FPT University SWE) is an enterprise **Infrastructure Inspection & Maintenance Management Platform**. It manages the end-to-end lifecycle of infrastructure inspection across bridges, wind turbines, power grids, and industrial facilities.

> **Note**: SmartDroneInspection is a **Business Management Platform** (Consumer), not a drone flight controller. Flight operations and drone hardware are managed by the external **SmartDroneHub** platform (AiTA Lab) via REST APIs.

---

## 🏗️ Architecture Options

The solution implements **Ardalis Clean Architecture** with two project structures:

### 1. Minimal Clean Architecture (`MinimalClean/` — Recommended)
A streamlined, single-project **Vertical Slice Architecture (VSA)** organized by business capabilities.
* **FastEndpoints (REPR Pattern)**: One endpoint class per file (`Request-Endpoint-Response`).
* **Ardalis.Specification**: Reusable, testable domain query specifications.
* **Pragmatic DDD**: Domain encapsulation, **Vogen Strongly-Typed IDs**, and **Ardalis.SmartEnum**.
* **Zero Merge Conflicts**: Each API endpoint is isolated in its own feature folder.

```text
backend/MinimalClean/src/MinimalClean.Architecture.Web/
├── Domain/                   # Pure Domain Model (No framework dependencies)
│   ├── Assets/               # Asset, AssetDocument, AssetLifecycleLog
│   ├── Missions/             # DroneMission, InspectionRequest, MissionTelemetry, MissionImage
│   ├── Planning/             # InspectionPlan, InspectionSchedule, CalendarEvent
│   ├── Reports/              # InspectionReport, Defect, MaintenanceTicket, TicketHistory
│   ├── Users/                # User, Organization, RefreshToken, AuditLog
│   └── Ai/                   # KnowledgeCase, KnowledgeCaseEmbedding, AiAnalysisJob
├── Infrastructure/           # Data access (EF Core 30 DbSets, pgvector HNSW, JWT, PBKDF2)
├── Features/                 # Vertical Slices (Auth, Assets, Missions, Reports, Tickets)
└── Configurations/           # DI, Serilog, Middleware, OpenAPI / Scalar UI
```

### 2. Full Clean Architecture (`src/Clean.Architecture.*`)
A traditional 4-layer separation (`Core`, `UseCases`, `Infrastructure`, `Web`) enforcing strict compiler-level dependency boundaries.

---

## 🚀 Key Business Modules

* **Asset Management (`/assets`)**: Hierarchical asset registry with GPS coordinates, categorization, and technical document tracking.
* **Inspection Planning & Requests (`/missions/requests`)**: Periodic scheduling, ad-hoc digital inspection requests, and manager approval flows.
* **Drone Mission Execution (`/missions`)**: Consuming SmartDroneHub REST APIs, real-time telemetry streaming via **SignalR**, and 4K image ingestion to **MinIO**.
* **Inspection Reports & Defect Detection (`/reports`)**: Automated defect detection via **DroneVisionAI (YOLOv8/11 + SAHI)**, inspector verification, and LLM-powered executive summarization.
* **Maintenance Work Orders (`/tickets`)**: Automated ticket generation from confirmed defects, technician assignment, and asset repair history.
* **AI Knowledge RAG (`/ai/knowledge`)**: Semantic retrieval of historical incidents and repair guidelines via **PostgreSQL pgvector (HNSW Cosine Index)**.
* **Authentication & RBAC (`/auth`)**: JWT Access Tokens, single-use Refresh Token rotation, and role-based permissions (5 roles: `Administrator`, `InspectionManager`, `Inspector`, `MaintenanceEngineer`, `Viewer`).

---

## 🛠️ Technology Stack

| Component | Technology |
| :--- | :--- |
| **Runtime** | .NET 9 / .NET 10 (C# 13) |
| **API Framework** | FastEndpoints (Minimal API / REPR Pattern) |
| **ORM & Database** | Entity Framework Core 10 + PostgreSQL 17 |
| **Vector Search** | `pgvector` with HNSW Cosine Index (`vector(1536)`) |
| **Object Storage** | MinIO (S3-compatible 4K inspection imagery) |
| **Real-time Comms** | ASP.NET Core SignalR |
| **Domain Modeling** | Vogen Strongly-Typed IDs + Ardalis.SmartEnum |
| **Data Querying** | Ardalis.Specification |
| **Validation** | FluentValidation |
| **Security** | JWT Bearer, PBKDF2 Password Hashing, Refresh Token Rotation |
| **Logging** | Serilog (Structured JSON & Console) |
| **API Documentation** | OpenAPI 3.0 + Scalar UI |

---

## ⚡ Quickstart & Local Setup

### 1. Start Infrastructure (PostgreSQL + pgvector, MinIO)

```bash
# PostgreSQL 17 with pgvector extension
docker run -d \
  --name smartdroneinspection-postgres \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=smartdroneinspection \
  -p 5432:5432 \
  pgvector/pgvector:pg17

# MinIO Object Storage
docker run -d \
  --name smartdroneinspection-minio \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  -p 9000:9000 -p 9001:9001 \
  minio/minio server /data --console-address ":9001"
```

### 2. Run Database Migrations

```bash
cd MinimalClean
dotnet ef database update -p src/MinimalClean.Architecture.Web -s src/MinimalClean.Architecture.Web
```

### 3. Run Backend API

```bash
dotnet run --project src/MinimalClean.Architecture.Web
```

* **Scalar API Reference**: `https://localhost:7080/scalar/v1`
* **Swagger UI**: `http://localhost:5080/swagger`
* **Health Check**: `https://localhost:7080/health`

---

## 📖 Documentation & Related Repositories

* **[SmartDroneInspection-Docs](https://github.com/tungbach12/SmartDroneInspection-Docs)**: Hugo documentation portal with architecture guides, ADRs, and team conventions.
* **[SmartDroneInspection-Frontend](https://github.com/tungbach12/SmartDroneInspection-Frontend)**: React 19 + TypeScript + Vite + MUI web portal.
* **[SmartDroneInspection-Mobile](https://github.com/tungbach12/SmartDroneInspection-Mobile)**: Flutter cross-platform mobile app.
