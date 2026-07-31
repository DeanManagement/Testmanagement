# PRD-022 — Frontend Stability & Consistency Bundle

| | |
|---|---|
| **Status** | Implemented (2026-06-10) — see §9 |
| **Author** | Engineering review (Claude) |
| **Created** | 2026-06-10 |
| **Priority** | P1 (leaks) / P2 (consistency) |
| **Target** | v1.4 |
| **Related** | CODE_REVIEW_2026-06-09.md §H4, §H5, §M4, §M5, §M6, §5 (low items); PRD-008 (usability bundle) |

---

## 1. Summary

The Angular app has **zero** uses of `takeUntilDestroyed`/`DestroyRef` — subscription cleanup is systemically absent rather than occasionally missed. Under zoneless change detection this means leaked subscriptions *and* stale callbacks firing into destroyed components. Bundled with it: the 401 redirect loop, untranslated interceptor messages, native `confirm()` dialogs, unbounded cross-page bulk selection, and the dirty-flag reset bug.

## 2. Current State (verified)

- `grep -r takeUntilDestroyed` → 0 hits. Concrete leaks: `test-case-list.component.ts:142,147,168` (folders$, queryParamMap, debounced search), `test-run-detail.component.ts:141`, `notification-bell` (4 subscriptions), `command-palette.component.ts:150,155`, dialog `afterClosed()` throughout; anti-pattern `subscribe(...).unsubscribe()` in `project-detail.component.ts:114-121`.
- `error.interceptor.ts`: hardcoded English snackbar strings (25/37/42); 401 → logout + navigate without checking the current route; login route matched by `req.url.includes(...)`.
- Native `confirm()` in `test-case-list.component.ts:251,337` (and siblings) vs Material dialogs elsewhere.
- Bulk selection accumulates IDs across pages without bound.
- `test-case-form.component.ts:~182-192`: `dirty=false` set before the save request; not restored on error → unsaved-changes guard disarmed after a failed save.
- Minor: blob URL revoked synchronously after `a.click()` (`test-run-report.component.ts:~64`); command-palette cache not cleared on logout.

## 3. Goals & Non-Goals

**Goals**
- One enforced subscription pattern; no component-level manual `subscribe` without lifecycle binding.
- Localized, loop-free error interceptor.
- Consistent Material confirm dialog; bounded bulk selection; correct dirty-state behavior.

**Non-Goals**
- Full migration to signals/`selectSignal` for all state (do it opportunistically where files are touched anyway; a wholesale rewrite is not justified).
- New features — this is a stability/consistency pass.

## 4. Proposed Design

### 4.1 Subscription sweep (H4)
House rules, applied across all components in one PR series:

| Case | Pattern |
|---|---|
| Store/state read for template | `store.selectSignal(...)` (removes the subscription entirely; fits zoneless) |
| Long-lived stream (queryParamMap, valueChanges, polls) | `.pipe(takeUntilDestroyed(this.destroyRef))` |
| One-shot (dialog `afterClosed`, single HTTP) | `.pipe(take(1))` — and `takeUntilDestroyed` too if the result mutates component state |

Replace `project-detail`'s `subscribe().unsubscribe()` with `selectSignal` + `computed`. Add an ESLint rule (`rxjs-angular/prefer-takeuntil` or a custom lint) to CI (PRD-023) so the pattern is enforced going forward.

### 4.2 Error interceptor (H5, M4)
- Translate all messages via `translate.instant(...)` keys (`errors.sessionExpired`, `errors.server`, `errors.network`) in en/de.
- 401: `if (!router.url.startsWith('/login'))` before navigating (the auto-logout half of this lives in PRD-020 §4.3).
- Replace the `includes('/api/auth/login')` string match with a shared route-constants module.

### 4.3 Confirm dialog (M4)
One `ConfirmDialogComponent` (title/message/params/danger-flag inputs) in `shared/`; replace all native `confirm()` call sites. Keyboard (Enter/Esc) and focus handling come free from MatDialog.

### 4.4 Bulk selection bound (M5)
Cap selection at a constant (e.g. 500) with a snackbar when hit; clear selection on filter/page change **unless** the user used "select all on page". Server-side "select all matching filter" is deferred (note in PRD-009 backlog).

### 4.5 Small fixes
- `dirty` restored to `true` in the save error handler (M6) — audit the other form components for the same pattern.
- Blob URL: `setTimeout(() => URL.revokeObjectURL(url))`.
- Command palette: clear cached items on logout (subscribe to auth state).

## 5. Edge Cases

- `takeUntilDestroyed` outside injection context requires passing `DestroyRef` explicitly — the sweep must touch call sites in callbacks (dialog handlers) carefully.
- `selectSignal` migration changes template references (`obs$ | async` → `sig()`); do per-component, not big-bang, to keep diffs reviewable.
- Confirm-dialog swap changes flow from synchronous `confirm()` to async — ensure dispatch only happens in the `afterClosed` callback (no fallthrough).

## 6. Testing

- Component tests asserting no emissions after destroy for the worst offenders (test-case-list, notification-bell, command-palette).
- Interceptor tests: 401 on login page does not navigate; messages resolve through ngx-translate (assert key, not string).
- Confirm dialog: cancel → no dispatch; confirm → dispatch.
- Form: failed save keeps the unsaved-changes guard armed.
- Lint rule active in CI; build has zero violations.

## 7. Effort & Risk

- **Effort:** M — the sweep is mechanical but wide (~25 components, ~2-3 days); the rest ~1 day.
- **Risk:** Low-medium — wide diff surface; mitigate by doing the sweep as several small PRs (per feature folder) with the lint rule landing last.

## 8. Acceptance Criteria

- [x] No component `subscribe` without lifecycle binding — sweep across ~30 components/guards (124 `takeUntilDestroyed` bindings, up from 0; one-shots additionally `take(1)`; both `subscribe().unsubscribe()` anti-patterns replaced). *Lint-rule enforcement deferred — no ESLint config exists in the project yet; CONTRIBUTING.md carries the rule meanwhile (follow-up: add angular-eslint + rxjs lint to CI).*
- [x] Interceptor messages localized (en + de); no 401 loop on the login page *(shipped early inside PRD-020)*.
- [x] All destructive confirmations use the new shared `ConfirmDialogComponent` (folder delete, bulk delete, webhook delete, unsaved-changes guard); zero native `confirm()` calls remain.
- [x] Bulk selection capped at 500 with a translated snackbar (select-all sliced, individual toggles blocked at the cap).
- [x] Failed saves restore the dirty flag in `test-case-form` (the only form with a component-level error path; the dispatch-based forms navigate on save and have no fixable error callback — noted for a future effects-error pass).

## 9. Implementation Notes (as shipped)

- Patterns: long-lived streams → `takeUntilDestroyed(destroyRef)`; one-shots (dialog `afterClosed`, single HTTP) → `take(1)` + `takeUntilDestroyed`; manual `Subscription` fields and `ngOnDestroy` boilerplate removed where they existed.
- Extras found during the sweep: a second `subscribe().unsubscribe()` anti-pattern in `watch-toggle`, the same blob-revoke bug in `test-suite-report` (fixed alongside `test-run-report`), and the export-blob revoke in `test-case-list`.
- `ConfirmDialogComponent` supports `titleKey/messageKey/messageParams/secondaryMessageKey/danger`; the unsaved-changes guard now returns the dialog observable (`CanDeactivateFn` accepts it natively).
- Intentionally unmanaged: `auth-image.pipe` (finite request warming a module-level blob cache), NgRx effects, root services — documented in code.
- Verified: `ng build` + `ng test` green; ~30 files changed.
