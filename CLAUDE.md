# CLAUDE.md

## Project Overview

**Testmanagement** is a self-hosted test management tool designed for simple organisations. It is licensed under MIT and maintained by DeanManagement.

## Current State

The project is in the **initial scaffolding stage**. No source code, build system, or tests exist yet. The repository contains only:

- `README.md` - project description
- `.gitignore` - configured for Java
- `LICENSE` - MIT license

## Intended Technology Stack

- **Language:** Java (inferred from `.gitignore` configuration)
- **Build system:** Not yet configured (Maven or Gradle expected)
- **Deployment:** Self-hosted

## Repository Structure

```
Testmanagement/
├── .gitignore          # Java-focused ignore rules
├── LICENSE             # MIT License
├── README.md           # Project description
└── CLAUDE.md           # This file
```

When source code is added, expect a standard Maven/Gradle layout:
```
src/
├── main/
│   ├── java/           # Application source code
│   └── resources/      # Configuration and static resources
└── test/
    ├── java/           # Test source code
    └── resources/      # Test resources
```

## Development Commands

No build system is configured yet. Once set up, update this section with:

- **Build:** (e.g., `mvn clean install` or `./gradlew build`)
- **Test:** (e.g., `mvn test` or `./gradlew test`)
- **Run:** (e.g., `mvn spring-boot:run` or `./gradlew bootRun`)
- **Lint/Format:** (e.g., `mvn checkstyle:check`)

## Code Conventions

### General

- Follow standard Java naming conventions (PascalCase for classes, camelCase for methods/variables)
- Keep changes minimal and focused on the task at hand
- Do not add unnecessary abstractions, comments, or features beyond what is requested

### Git

- Write clear, descriptive commit messages
- Keep commits focused on a single logical change
- Do not commit secrets, credentials, or environment-specific configuration files

### Testing

- Tests should be added alongside any new functionality
- Follow the project's test framework conventions once established

## Environment Setup

No environment configuration exists yet. When added, document:

- Required Java version
- Database setup (if applicable)
- Environment variables
- Docker/container setup (if applicable)

## CI/CD

No CI/CD pipelines are configured. When added, document pipeline stages and requirements here.

## Key Architectural Decisions

None recorded yet. Document significant decisions here as they are made (e.g., choice of web framework, database, authentication approach).
