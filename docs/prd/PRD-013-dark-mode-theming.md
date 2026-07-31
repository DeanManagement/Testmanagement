# PRD-013 — Dark Mode / Theming

| | |
|---|---|
| **Status** | Proposed |
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
- [ ] Light/dark/system toggle in the header; choice persists across reloads.
- [ ] First visit honors the OS preference.
- [ ] No unreadable (hardcoded-color) elements in dark mode; charts re-theme on switch.
- [ ] Theme service unit test passes; build clean.
