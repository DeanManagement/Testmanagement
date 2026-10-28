import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { forkJoin, Observable } from 'rxjs';
import { take } from 'rxjs/operators';
import { ResultStatusApiService } from '../../core/services/result-status-api.service';
import { ProjectResultStatus } from '../../shared/models/result-status.model';
import { TestResultStatus } from '../../shared/models/test-run.model';

const NEW_STATUS_COLOR = '#2563eb';

/**
 * TM24: a project's result statuses — add, rename, recolour, reorder, archive. Each names one fixed
 * category, chosen when it is added, and every metric counts that category.
 */
@Component({
  selector: 'app-result-status-settings',
  standalone: true,
  imports: [RouterLink, FormsModule, DragDropModule, MatButtonModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatSelectModule, MatSlideToggleModule, MatTooltipModule, TranslateModule],
  template: `
    <div class="container">
      <div class="page-header">
        <a mat-button [routerLink]="['/projects', projectId]" data-test-id="result-status-back">
          <mat-icon>arrow_back</mat-icon>{{ 'common.back' | translate }}
        </a>
        <h1>{{ 'resultStatusSettings.title' | translate }}</h1>
      </div>
      <p class="hint">{{ 'resultStatusSettings.description' | translate }}</p>

      <form class="tm-card add-card" (ngSubmit)="add()">
        <mat-form-field appearance="outline" class="grow" subscriptSizing="dynamic">
          <mat-label>{{ 'resultStatusSettings.name' | translate }}</mat-label>
          <input matInput name="newName" [(ngModel)]="newName" maxlength="100" data-test-id="result-status-new-name">
        </mat-form-field>
        <mat-form-field appearance="outline" subscriptSizing="dynamic">
          <mat-label>{{ 'resultStatusSettings.category' | translate }}</mat-label>
          <mat-select name="newCategory" [(ngModel)]="newCategory" data-test-id="result-status-new-category">
            @for (c of categories; track c) {
              <mat-option [value]="c">{{ 'resultStatus.' + c | translate }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <input type="color" name="newColor" [(ngModel)]="newColor" [attr.aria-label]="'resultStatusSettings.color' | translate">
        <button mat-flat-button type="submit" [disabled]="!newName.trim()" data-test-id="result-status-add-btn">
          <mat-icon>add</mat-icon>{{ 'resultStatusSettings.add' | translate }}
        </button>
      </form>

      <div class="tm-card list" cdkDropList (cdkDropListDropped)="drop($event)" data-test-id="result-status-list">
        @for (s of statuses; track s.id) {
          <div class="row" cdkDrag [class.archived]="s.archived" data-test-id="result-status-row">
            <mat-icon class="drag-handle" cdkDragHandle [attr.aria-label]="'resultStatusSettings.reorder' | translate">drag_indicator</mat-icon>
            <input type="color" [value]="s.color" (change)="save(s, { color: $any($event.target).value })"
                   [attr.aria-label]="'resultStatusSettings.color' | translate">
            <input class="name" [value]="s.name" maxlength="100" (change)="rename(s, $any($event.target).value)"
                   [attr.aria-label]="'resultStatusSettings.name' | translate" data-test-id="result-status-name">
            <span class="badge badge--{{ s.category.toLowerCase() }} badge--small">{{ 'resultStatus.' + s.category | translate }}</span>
            <mat-slide-toggle [checked]="!s.archived" (change)="save(s, { archived: !$event.checked })"
                              [matTooltip]="'resultStatusSettings.activeHint' | translate"
                              data-test-id="result-status-active-toggle"></mat-slide-toggle>
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .container { max-width: 900px; margin: 0 auto; padding: 1.5rem; }
    .page-header { display: flex; align-items: center; gap: 0.75rem; }
    .hint { color: var(--tm-text-secondary); margin: 0.25rem 0 1.25rem; }
    .tm-card { border: 1px solid var(--tm-border); border-radius: 12px; padding: 1rem 1.25rem; margin-bottom: 1rem;
      background: var(--tm-surface); }
    .add-card { display: flex; align-items: center; flex-wrap: wrap; gap: 0.75rem; }
    .grow { flex: 1 1 14rem; }
    .row { display: flex; align-items: center; gap: 0.75rem; padding: 0.5rem 0; background: var(--tm-surface); }
    .row + .row { border-top: 1px solid var(--tm-border); }
    .row.archived .name { color: var(--tm-text-secondary); text-decoration: line-through; }
    .drag-handle { cursor: grab; color: var(--tm-text-secondary); }
    .name { flex: 1; min-width: 0; font: inherit; color: inherit; background: transparent; border: 1px solid transparent;
      border-radius: 6px; padding: 4px 6px; }
    .name:hover, .name:focus { border-color: var(--tm-border); }
    input[type='color'] { width: 32px; height: 32px; padding: 0; border: none; background: none; cursor: pointer; }
    .cdk-drag-placeholder { opacity: 0.3; }
  `],
})
export class ResultStatusSettingsComponent implements OnInit {
  private readonly api = inject(ResultStatusApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly categories: TestResultStatus[] = ['PENDING', 'PASSED', 'FAILED', 'BLOCKED', 'SKIPPED'];

  projectId = '';
  statuses: ProjectResultStatus[] = [];
  newName = '';
  newCategory: TestResultStatus = 'SKIPPED';
  newColor = NEW_STATUS_COLOR;

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  load(): void {
    this.api.getAll(this.projectId).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe((statuses) => {
      this.statuses = statuses;
      this.cdr.detectChanges();
    });
  }

  add(): void {
    const name = this.newName.trim();
    if (!name) {
      return;
    }
    this.reloadAfter(this.api.create(this.projectId, { name, color: this.newColor, category: this.newCategory }),
      () => this.newName = '');
  }

  rename(status: ProjectResultStatus, value: string): void {
    const name = value.trim();
    if (!name || name === status.name) {
      this.load();
      return;
    }
    this.save(status, { name });
  }

  save(status: ProjectResultStatus, change: { name?: string; color?: string; archived?: boolean }): void {
    this.reloadAfter(this.api.update(this.projectId, status.id, change));
  }

  /** Each status's position becomes its place in the list; only the ones that moved are saved. */
  drop(event: CdkDragDrop<ProjectResultStatus[]>): void {
    moveItemInArray(this.statuses, event.previousIndex, event.currentIndex);
    const moved = this.statuses
      .map((s, position) => ({ s, position }))
      .filter(({ s, position }) => s.position !== position);
    if (moved.length) {
      this.reloadAfter(forkJoin(moved.map(({ s, position }) => this.api.update(this.projectId, s.id, { position }))));
    }
  }

  /** Reloads either way: on a refusal (a duplicate name, the last status of a category) the list snaps back. */
  private reloadAfter(call: Observable<unknown>, onSaved?: () => void): void {
    call.pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        onSaved?.();
        this.load();
      },
      error: () => this.load(),
    });
  }
}
