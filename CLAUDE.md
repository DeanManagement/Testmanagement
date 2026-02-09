# CLAUDE.md

## Project Overview

**Testmanagement** is a self-hosted test management tool designed for simple organisations. It is licensed under MIT and maintained by DeanManagement.

## Technology Stack

- **Backend:** Java 21, Spring Boot 3.5.x, Maven
- **Frontend:** Angular 19, Angular Material, NgRx, Chart.js
- **Database:** PostgreSQL 16
- **Auth:** OIDC via Keycloak (external)
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
│       │   ├── config/       # Security, OIDC, CORS, JPA auditing
│       │   ├── controller/   # REST controllers
│       │   ├── dto/          # Request/Response DTOs + MapStruct mappers
│       │   ├── entity/       # JPA entities + enums
│       │   ├── repository/   # Spring Data JPA repositories
│       │   ├── service/      # Business logic
│       │   └── exception/    # Custom exceptions & error handling
│       └── main/resources/
│           ├── application.yml
│           ├── application-dev.yml
│           └── db/migration/ # Flyway SQL migrations (V1-V10)
├── frontend/                 # Angular 19 project
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

### Backend

- **Build:** `cd backend && ./mvnw clean package`
- **Build (skip tests):** `cd backend && ./mvnw clean package -DskipTests`
- **Run (dev, no Keycloak needed):** `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
- **Run tests:** `cd backend && ./mvnw test`
- **Run single test:** `cd backend && ./mvnw test -Dtest=ProjectControllerTest`

### Frontend

- **Install deps:** `cd frontend && npm install`
- **Dev server:** `cd frontend && npx ng serve` (proxy to backend on :8080)
- **Build:** `cd frontend && npx ng build`
- **Run tests:** `cd frontend && npx ng test`

### Docker

- **Full stack:** `docker compose up --build`
- **Just database:** `docker compose up testmanagement-db`

## Code Conventions

### General

- Follow standard Java naming conventions (PascalCase for classes, camelCase for methods/variables)
- Keep changes minimal and focused on the task at hand
- Do not add unnecessary abstractions, comments, or features beyond what is requested

### Backend

- Use Lombok for entities (@Getter, @Setter, @NoArgsConstructor)
- Use MapStruct for DTO mapping
- Use Java records for DTOs (CreateXxxRequest, UpdateXxxRequest, XxxResponse)
- Flyway migrations named `V{n}__{description}.sql`
- Dev profile (`spring.profiles.active=dev`) uses H2 in-memory DB and disables OIDC

### Frontend

- Angular 19 standalone components (no NgModules)
- NgRx with functional API (createActionGroup, createReducer, createEffect)
- ngx-translate for i18n (en.json, de.json)
- Angular Material for UI components

### Git

- Write clear, descriptive commit messages
- Keep commits focused on a single logical change
- Do not commit secrets, credentials, or environment-specific configuration files

## Environment Setup

- **Java:** 21 (Eclipse Temurin recommended)
- **Node.js:** 20+ LTS
- **Database:** PostgreSQL 16 (via Docker or local install)
- **Keycloak:** External instance (not required for dev profile)

## Key Architectural Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| DB IDs | UUID | Air-gap/distributed friendly |
| DTO mapping | MapStruct | Type-safe, compile-time |
| Boilerplate | Lombok | Reduces entity verbosity |
| State management | NgRx with EntityAdapter | Scalable, normalized state |
| i18n | ngx-translate | Runtime language switching |
| Charts | Chart.js | Simpler than D3.js for standard charts |
| Dev DB | H2 in PostgreSQL mode | No external DB needed for dev |
| Column `key` | Mapped to `project_key` | `key` is reserved in H2 |
