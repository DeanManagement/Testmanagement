# Contributing

## Running tests

- Backend: `cd backend && ./mvnw test` (Java 25; tests run on H2, no database needed)
- Single test: `./mvnw test -Dtest=ProjectControllerTest`
- Frontend: `cd frontend && npm ci && npx ng test --watch=false`
- Full stack: `cp .env.example .env` (fill in secrets) then `docker compose up --build`

CI (GitHub Actions) runs all of the above plus the image build, a Trivy scan, a check that the jar
actually contains the built SPA, and a compose-level smoke test on every push and PR. A red
pipeline blocks merging.

`scripts/check-frontend-bundle.sh` inspects the **production build output**, which is where a
whole class of bug lives that no unit test can see: an external CDN reference, or a stylesheet
deferred behind an inline `onload` handler. Both pass every test and then break at runtime under
the app's own CSP. Both pipelines call the same script so they cannot drift; run it yourself with
`cd frontend && npx ng build && cd .. && bash scripts/check-frontend-bundle.sh`.

Woodpecker (`.woodpecker.yml`) runs the same tests on every branch push and, from `main` or a
manual run, builds and pushes the `testmanagement` image to Nexus, tagged `latest` and the
short commit SHA. There is one image: the root Dockerfile builds the Angular app and bakes it
into the Spring Boot jar, which serves the SPA and the API on one port. Test and coverage
reports go to the Nexus `ci-reports` raw repository under `testmanagement/<short-sha>/`,
because Woodpecker has no artifact store. It needs two repo secrets, `nexus_user` and `nexus_password`; the buildx plugin
must be trusted as privileged on the server.

## Pull request checklist

- [ ] Backend tests green (`./mvnw test`), frontend tests + build green
- [ ] If any dependency changed, `npm ci` verified in a clean directory. `ng test` and `ng build`
      reuse the existing `node_modules` and pass happily against a lockfile that `npm ci` rejects
      as out of sync — which is what CI runs, so the break only shows up there
- [ ] New endpoints enforce project authorization (`@RequireProjectRole` or a service-level
      check — see `docs/prd/PRD-001`)
- [ ] New Flyway migration numbered after the highest existing `V{n}` **across both
      `db/migration/` and `db/specific/*/`** — Flyway merges them into one timeline, so a version
      reused between the two aborts startup. `MigrationVersionsTest` enforces this. Postgres-only
      SQL goes in `db/specific/postgresql/`
- [ ] New user-facing strings added to **both** `frontend/src/assets/i18n/en.json` and `de.json`
- [ ] Frontend subscriptions lifecycle-bound (`takeUntilDestroyed` / `take(1)` / `selectSignal`)
- [ ] Component state written from an async callback is a **signal**, not a plain field. The app is
      zoneless: an HTTP callback assigning `this.foo = …` notifies nothing, so the view is never
      re-checked and the old value stays on screen — a spinner that never clears, an image that
      never appears. Signal writes schedule change detection themselves
- [ ] Colours use an existing `--tm-*` token (see Theming below) — no hex literals, no `--mat-sys-*`
- [ ] Checked in **both** light and dark mode
- [ ] Any new static path is either content-hashed (cached immutably) or served `no-cache`.
      `index.html` names its bundles by content hash, so a shell that outlives a release asks for
      hashes the new jar does not have, every one falls through to the SPA fallback and comes back
      as HTML, and the app renders a blank page full of `Refused to apply style … MIME type
      ('text/html')` — against a perfectly healthy server. `ShellSecurityHeadersFilter` decides
      this; `SpaServingTest` pins it
- [ ] No external CDN references (fonts, scripts, styles). The app must run air-gapped, and its
      own CSP allows `'self'` only — an external reference is silently dropped at runtime
- [ ] No secrets, credentials, or environment-specific config committed
- [ ] Test fixtures standing in for credentials do **not** imitate a real provider's format. A
      `glpat-` prefix followed by exactly 20 token-characters is indistinguishable from a live
      GitLab token, and GitHub push protection rejected the whole branch over one. Use something
      obviously fake — the code under test only ever sees an opaque string

## Theming

`styles.scss` defines `--tm-*` custom properties on `:root`; `.tm-dark` redefines the same
names, and `ThemeService` toggles that class on `<html>`. Any component that uses the tokens
re-themes for free. Three rules, each of which has already been broken once:

1. **Never write a hex literal in a component stylesheet.** A hardcoded light background is
   the one thing dark mode cannot recover from.
2. **Never reference a `--mat-sys-*` variable.** This app applies `mat.all-component-colors`,
   which does not emit the Material 3 system tokens — so `var(--mat-sys-surface, #fff)`
   silently resolves to the *fallback* and paints a white panel in dark mode. If a Material
   component needs overriding, use its `--mat-<component>-*` token and verify in the browser
   that it actually resolves.
3. **Watch the direction a token points.** `--tm-primary` is a *foreground* colour and
   inverts between themes. For a dark surface carrying light text in both themes use
   `--tm-brand-surface` / `--tm-brand-surface-2`. For a label drawn on a solid accent or
   success fill use `--tm-on-accent` / `--tm-on-success`, which flip to dark text where the
   fill gets lighter.

Status badges in `styles.scss` are the deliberate exception: they keep explicit per-theme
pairs, because the tint itself carries the meaning. A new status needs adding to both blocks.

Chart.js: call `applyChartDefaults(Chart)` from `core/utils/chart-theme.ts` in any render
method and re-render on `ThemeService.resolvedChanges`.

## Releases

Tag `vX.Y.Z` on main → the release workflow re-runs tests, pushes versioned images to GHCR,
and creates a GitHub release with generated notes.

## Conventions

See `CLAUDE.md` for code conventions (Lombok, MapStruct, records for DTOs, standalone
Angular components, NgRx functional API) and `docs/prd/` for the PRD-driven feature history.
