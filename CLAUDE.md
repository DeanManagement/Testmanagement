# CLAUDE.md

## Project Overview

**Testmanagement** is a self-hosted test management tool designed for simple organisations. It is licensed under MIT and maintained by DeanManagement.

## Technology Stack

- **Backend:** Java 25, Spring Boot 4.0.x, Maven
- **Frontend:** Angular 21, Angular Material, NgRx, Chart.js
- **Database:** PostgreSQL 16
- **Auth:** Local username/password with JWT, plus optional multi-provider OIDC SSO configured at runtime by an admin (PRD-012)
- **Packaging:** Docker, docker-compose

## Repository Structure

```
Testmanagement/
├── backend/                  # Spring Boot Maven project
│   ├── pom.xml
│   ├── Dockerfile
│   ├── mvnw / mvnw.cmd
│   └── src/
│       ├── main/java/com/deanmanagement/testmanagement/
│       │   ├── user/         # Users, auth, API keys, SSO — package-by-feature
│       │   ├── project/      # Projects, test cases/suites/runs/plans, webhooks, CI, issue tracker
│       │   └── shared/       # Cross-cutting: crypto, net (SSRF guards), config, exceptions
│       └── main/resources/
│           ├── application.yml
│           ├── application-dev.yml
│           ├── db/migration/ # Flyway SQL migrations (V1-V46), vendor-neutral
│           └── db/specific/  # Vendor-only migrations ({vendor} = postgresql | h2)
├── frontend/                 # Angular 21 project
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── proxy.conf.json
│   └── src/app/
│       ├── core/             # Interceptors, guards, services
│       ├── shared/           # Shared models, components, pipes
│       ├── features/         # Feature modules (projects, etc.)
│       └── store/            # NgRx store (actions, reducers, effects, selectors)
├── docker-compose.yml
├── REQUIREMENTS.md
├── CLAUDE.md
├── README.md
└── LICENSE
```

## Development Commands

### Run the whole stack locally

1. **Database:** `docker compose up -d testmanagement-db` (Postgres on `localhost:5432`)
2. **Backend + frontend:** `./scripts/dev.sh` — starts both with prefixed logs; Ctrl-C stops both.
   Use `./scripts/dev.sh backend` or `./scripts/dev.sh frontend` to run just one.

Backend serves on `:8089`, frontend dev server on `:4200` with `/api` proxied to `:8089`.
Log in as `admin@localhost.ch` / `admin` (dev-profile default; `ADMIN_PASSWORD` overrides).

### Backend

- **Build:** `cd backend && ./mvnw clean package`
- **Build (skip tests):** `cd backend && ./mvnw clean package -DskipTests`
- **Run (dev):** `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` (needs the Postgres container running)
- **Run tests:** `cd backend && ./mvnw test`
- **Run single test:** `cd backend && ./mvnw test -Dtest=ProjectControllerTest`

### Frontend

- **Install deps:** `cd frontend && npm install`
- **Dev server:** `cd frontend && npx ng serve` (proxy to backend on :8089)
- **Build:** `cd frontend && npx ng build`
- **Run tests:** `cd frontend && npx ng test` (Vitest + jsdom)

### Docker

- **Full stack:** `docker compose up --build` (frontend on :8012, backend on :8089)
- **Just database:** `docker compose up -d testmanagement-db`

## Code Conventions

### General

- Follow standard Java naming conventions (PascalCase for classes, camelCase for methods/variables)
- Keep changes minimal and focused on the task at hand
- Do not add unnecessary abstractions, comments, or features beyond what is requested

### Backend

- Package-by-feature: top-level modules (`user`, `project`, `shared`) keep implementation under
  `internal/`; only types outside `internal/` are a module's public surface
- Use Lombok for entities (@Getter, @Setter, @NoArgsConstructor)
- Use MapStruct for DTO mapping
- Use Java records for DTOs (CreateXxxRequest, UpdateXxxRequest, XxxResponse)
- Flyway migrations named `V{n}__{description}.sql` in `db/migration/`. Vendor-specific SQL goes in
  `db/specific/{vendor}/` — never nested under `db/migration/`, which Flyway scans recursively
- New tables need `created_by` / `updated_by` columns (BaseEntity) or Hibernate schema validation fails
- Dev profile (`spring.profiles.active=dev`) points at the local Postgres container and sets a
  throwaway JWT secret plus a default admin password. Tests run against H2 in PostgreSQL mode

### Frontend

- Angular 21 standalone components (no NgModules), zoneless change detection
- NgRx with functional API (createActionGroup, createReducer, createEffect)
- ngx-translate for i18n (en.json, de.json)
- Angular Material for UI components

### Git

- Write clear, descriptive commit messages
- Keep commits focused on a single logical change
- Do not commit secrets, credentials, or environment-specific configuration files

## Environment Setup

- **Java:** 25 (Eclipse Temurin recommended)
- **Node.js:** 22+ LTS
- **Database:** PostgreSQL 16 — required for dev too (`docker compose up -d testmanagement-db`)
- **OIDC provider:** Optional, configured at runtime in the admin UI. Not needed for dev.

## Key Architectural Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| DB IDs | UUID | Air-gap/distributed friendly |
| DTO mapping | MapStruct | Type-safe, compile-time |
| Boilerplate | Lombok | Reduces entity verbosity |
| State management | NgRx with EntityAdapter | Scalable, normalized state |
| i18n | ngx-translate | Runtime language switching |
| Charts | Chart.js | Simpler than D3.js for standard charts |
| Dev DB | Real PostgreSQL 16 via Docker | Dev matches prod; full-text search and vendor SQL behave the same |
| Test DB | H2 in PostgreSQL mode | Fast, no container needed in CI |
| Column `key` | Mapped to `project_key` | `key` is reserved in H2 |
| Code layout | Package-by-feature with `internal/` | Keeps module boundaries explicit |
