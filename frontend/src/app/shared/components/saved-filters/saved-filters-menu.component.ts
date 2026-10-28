import { Component, computed, DestroyRef, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { filter, switchMap, take } from 'rxjs/operators';
import { SavedFilterApiService } from '../../../core/services/saved-filter-api.service';
import { SavedFilter, SavedFilterView } from '../../models/saved-filter.model';
import { ConfirmDialogComponent, ConfirmDialogData } from '../confirm-dialog/confirm-dialog.component';
import {
  SavedFilterDialogComponent,
  SavedFilterDialogData,
  SavedFilterDialogResult,
} from './saved-filter-dialog.component';
import { filterQueryOf, urlWithFilter } from './saved-filter-url';

/**
 * Saved and shared filters for a URL-bound list (PRD-053 §3): the list's query string is its whole
 * filter state, so saving stores it and applying navigates to it. Lists the caller's own filters and
 * the ones shared in the project.
 */
@Component({
  selector: 'app-saved-filters-menu',
  standalone: true,
  imports: [MatButtonModule, MatDividerModule, MatIconModule, MatMenuModule, MatTooltipModule, TranslateModule],
  template: `
    <button mat-stroked-button type="button" [matMenuTriggerFor]="menu" (menuOpened)="load()"
            data-test-id="saved-filters-btn">
      <mat-icon>bookmarks</mat-icon>
      {{ 'savedFilters.button' | translate }}
    </button>
    <mat-menu #menu="matMenu" class="saved-filters-menu">
      @for (section of sections(); track section.titleKey) {
        <div class="menu-heading">{{ section.titleKey | translate }}</div>
        @for (saved of section.filters; track saved.id) {
          <div class="filter-row">
            <button mat-menu-item type="button" class="filter-apply" (click)="apply(saved)"
                    [attr.data-test-id]="'saved-filter-' + saved.name">
              @if (saved.shared) {
                <mat-icon [matTooltip]="'savedFilters.sharedHint' | translate">group</mat-icon>
              }
              {{ saved.name }}
            </button>
            @if (saved.editable) {
              <button mat-icon-button type="button" (click)="edit(saved)" [attr.aria-label]="'savedFilters.edit' | translate"
                      [matTooltip]="'savedFilters.edit' | translate" [attr.data-test-id]="'saved-filter-edit-' + saved.name">
                <mat-icon>edit</mat-icon>
              </button>
              <button mat-icon-button type="button" (click)="remove(saved)" [attr.aria-label]="'common.delete' | translate"
                      [matTooltip]="'common.delete' | translate" [attr.data-test-id]="'saved-filter-delete-' + saved.name">
                <mat-icon>delete</mat-icon>
              </button>
            }
          </div>
        } @empty {
          <div class="menu-hint">{{ section.emptyKey | translate }}</div>
        }
      }
      <mat-divider />
      <button mat-menu-item type="button" (click)="saveCurrent()" data-test-id="saved-filters-save">
        <mat-icon>bookmark_add</mat-icon>
        {{ 'savedFilters.saveCurrent' | translate }}
      </button>
    </mat-menu>
  `,
  styles: [`
    .menu-heading { padding: 8px 16px 4px; font-size: 12px; font-weight: 600; color: var(--tm-text-secondary);
      text-transform: uppercase; }
    .menu-hint { padding: 4px 16px 8px; font-size: 13px; color: var(--tm-text-secondary); }
    .filter-row { display: flex; align-items: center; }
    .filter-apply { flex: 1; min-width: 0; }
  `],
})
export class SavedFiltersMenuComponent {
  private readonly api = inject(SavedFilterApiService);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  readonly projectId = input.required<string>();
  readonly view = input.required<SavedFilterView>();

  readonly filters = signal<SavedFilter[]>([]);
  /** Own filters (shared or not) first, then the ones colleagues shared. */
  readonly sections = computed(() => [
    { titleKey: 'savedFilters.mine', emptyKey: 'savedFilters.noneMine', filters: this.filters().filter((f) => f.mine) },
    { titleKey: 'savedFilters.shared', emptyKey: 'savedFilters.noneShared',
      filters: this.filters().filter((f) => !f.mine) },
  ]);

  /** Fetched on every open, so a filter a colleague just shared shows up. */
  load(): void {
    this.api.getVisible(this.projectId(), this.view()).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (filters) => this.filters.set(filters), error: () => this.filters.set([]) });
  }

  apply(saved: SavedFilter): void {
    this.router.navigateByUrl(urlWithFilter(this.router.url, saved.queryString));
  }

  saveCurrent(): void {
    const queryString = filterQueryOf(this.router.url);
    this.askNameAndSharing({ titleKey: 'savedFilters.saveTitle', name: '', shared: false })
      .pipe(switchMap((result) => this.api.create(this.projectId(),
        { ...result, view: this.view(), queryString })))
      .subscribe(() => this.load());
  }

  edit(saved: SavedFilter): void {
    this.askNameAndSharing({ titleKey: 'savedFilters.editTitle', name: saved.name, shared: saved.shared })
      .pipe(switchMap((result) => this.api.update(this.projectId(), saved.id, result)))
      .subscribe(() => this.load());
  }

  remove(saved: SavedFilter): void {
    const data: ConfirmDialogData = {
      titleKey: 'savedFilters.deleteTitle',
      messageKey: 'savedFilters.deleteMessage',
      messageParams: { name: saved.name },
      danger: true,
    };
    this.dialog.open(ConfirmDialogComponent, { data }).afterClosed()
      .pipe(take(1), filter(Boolean), switchMap(() => this.api.delete(this.projectId(), saved.id)),
        takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load());
  }

  private askNameAndSharing(data: SavedFilterDialogData) {
    return this.dialog.open<SavedFilterDialogComponent, SavedFilterDialogData, SavedFilterDialogResult>(
      SavedFilterDialogComponent, { data }).afterClosed()
      .pipe(take(1), filter((result): result is SavedFilterDialogResult => !!result),
        takeUntilDestroyed(this.destroyRef));
  }
}
