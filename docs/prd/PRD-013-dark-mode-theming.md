# PRD-013 — Dark Mode / Theming

| | |
|---|---|
| **Status** | ✅ Implemented (2026-07-31) |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-09 |
| **Priority** | P3 — v2.0, low effort / high satisfaction (do first) |
| **Target** | v2.0 |
| **Related** | REQUIREMENTS.md §13.7 (was PRD-009 §2.4) |

---

## 1. Summary

A light/dark theme toggle. Frontend-only, no backend, self-contained — the cheapest item in the v2 set and frequently appreciated. Recommended as the first v2 pickup.

## 2. Goals & Non-Goals

**Goals**
- Light and dark Angular Material themes.
- A toggle in the nav header; preference persisted in `localStorage`.
- Honor the OS preference (`prefers-color-scheme`) on first visit, then respect the explicit choice.
- Charts and custom components readable in both themes.

**Non-Goals**
- Per-user server-stored preference (local only).
- Arbitrary/custom color themes or white-labeling.

## 3. Proposed Design

- Define light + dark Material theme tokens (Angular Material theming / CSS custom properties). Apply a `dark` class (or `color-scheme`) on the document root.
- `ThemeService`: reads `localStorage('theme')` ∈ `light|dark|system`; resolves `system` via `matchMedia('(prefers-color-scheme: dark)')` and listens for OS changes while in `system` mode; sets the root class.
- Nav header toggle (light/dark/system) wired to `ThemeService`.
- Audit custom SCSS for hardcoded colors; replace with theme CSS variables (`var(--mat-sys-*)` and existing app variables already used in PRD-008 styles). Chart.js datasets: derive grid/text/legend colors from the resolved theme and re-render on theme change (mirror the `onLangChange` re-render added in PRD-008).

## 4. Edge Cases
- No `localStorage` (private mode) → fall back to `system`, don't crash.
- Theme switch while a chart is open → charts re-render with theme-aware colors.
- Print/PDF export styles remain light (report PDFs are generated server-side and unaffected).

## 5. Testing
- `ThemeService` resolves `system`/`light`/`dark` and persists the choice (unit test with mocked `matchMedia`/`localStorage`).
- Toggle updates the root class; frontend builds clean.
- Manual: spot-check key screens + charts in both themes for contrast (WCAG AA).

## 6. Effort & Risk
- **Effort:** ~2 days.
- **Risk:** Low — frontend-only; main effort is finding hardcoded colors. Reuses the chart re-render hook from PRD-008.

## 7. Acceptance Criteria
- [x] Light/dark/system toggle in the header; choice persists across reloads.
- [x] First visit honors the OS preference.
- [x] No unreadable (hardcoded-color) elements in dark mode; charts re-theme on switch.
- [x] Theme service unit test passes; build clean.

## 8. As Built

- `ThemeService` (`core/services/theme.service.ts`) holds the `light|dark|system` preference in
  `localStorage` under `tm-theme`, resolves `system` through `matchMedia` and keeps listening for OS
  changes while that preference is active. It toggles `.tm-dark` and `color-scheme` on `<html>`, and
  publishes `resolvedChanges` for consumers that must re-render.
- Applied from `provideAppInitializer`, so the theme is on the document before the first paint.
- `styles.scss` defines the dark palette as overrides of the existing `--tm-*` tokens, so every
  component already using those variables re-themes without changes. Angular Material gets
  `mat.all-component-colors($dark-theme)` under `html.tm-dark` — colors only, since density and
  typography are theme-independent and re-emitting them would double the stylesheet.
- Added semantic tint tokens (`--tm-tint-{neutral,success,warn,danger,info}-{bg,fg}`, `--tm-subtle-bg`)
  and moved the hardcoded banner, chip and status-cell colors in eleven component stylesheets onto
  them. Status badges keep explicit per-theme pairs, because there the tint itself carries meaning.
- Charts re-render on `resolvedChanges`; `core/utils/chart-theme.ts` points Chart.js's global
  defaults at the current CSS variables so ticks, legends, grids and tooltips follow the theme, while
  dataset colors — which encode status — stay fixed.
- Contrast: every dark foreground/background pair used for text measures at least 5.6:1, above the
  4.5:1 AA threshold.
- Print styles force the light palette, so PDF output is unaffected by the active theme.
- Tests: six `ThemeService` cases covering default resolution, persistence, OS tracking before and
  after an explicit choice, change de-duplication, and the private-browsing path where
  `localStorage` throws.
