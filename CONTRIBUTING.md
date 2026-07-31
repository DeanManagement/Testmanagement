# Contributing

## Running tests

- Backend: `cd backend && ./mvnw test` (Java 25; tests run on H2, no database needed)
- Single test: `./mvnw test -Dtest=ProjectControllerTest`
- Frontend: `cd frontend && npm ci && npx ng test --watch=false`
- Full stack: `cp .env.example .env` (fill in secrets) then `docker compose up --build`

CI (GitHub Actions) runs all of the above plus Docker builds, a Trivy image scan, and a
compose-level smoke test on every push and PR. A red pipeline blocks merging.

## Pull request checklist

- [ ] Backend tests green (`./mvnw test`), frontend tests + build green
- [ ] New endpoints enforce project authorization (`@RequireProjectRole` or a service-level
      check — see `docs/prd/PRD-001`)
- [ ] New Flyway migration numbered after the highest existing `V{n}` (check `db/migration/`);
      Postgres-only SQL goes in `db/specific/postgresql/`
- [ ] New user-facing strings added to **both** `frontend/src/assets/i18n/en.json` and `de.json`
- [ ] Frontend subscriptions lifecycle-bound (`takeUntilDestroyed` / `take(1)` / `selectSignal`)
- [ ] No secrets, credentials, or environment-specific config committed

## Releases

Tag `vX.Y.Z` on main → the release workflow re-runs tests, pushes versioned images to GHCR,
and creates a GitHub release with generated notes.

## Conventions

See `CLAUDE.md` for code conventions (Lombok, MapStruct, records for DTOs, standalone
Angular components, NgRx functional API) and `docs/prd/` for the PRD-driven feature history.
