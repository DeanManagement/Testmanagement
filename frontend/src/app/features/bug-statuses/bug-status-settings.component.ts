import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { forkJoin, Observable } from 'rxjs';
import { take } from 'rxjs/operators';
import { BugStatusApiService } from '../../core/services/bug-status-api.service';
import {
  ALL_BUG_STATUSES, BugReportStatus, BugStatus, BugStatusTransition, sortBugStatuses,
} from '../../shared/models/bug-report.model';

const NEW_STATUS_COLOR = '#0ea5e9';

/**
 * TM47: a project's bug statuses — add, rename, recolour, reorder, archive — and which status may
 * follow which. Each status counts as one fixed category; with no transitions ticked, any move is
 * allowed.
 */
@Component({
  selector: 'app-bug-status-settings',
  standalone: true,
  imports: [RouterLink, FormsModule, DragDropModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatSelectModule, MatSlideToggleModule, MatTooltipModule, TranslateModule],
  templateUrl: './bug-status-settings.component.html',
  styleUrl: './bug-status-settings.component.scss',
})
export class BugStatusSettingsComponent implements OnInit {
  private readonly api = inject(BugStatusApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly categories = ALL_BUG_STATUSES;

  projectId = '';
  statuses: BugStatus[] = [];
  newName = '';
  newCategory: BugReportStatus = 'RESOLVED';
  newColor = NEW_STATUS_COLOR;
  /** "from|to" keys of the moves allowed; empty means any move. */
  allowed = new Set<string>();
  savedAllowed = new Set<string>();

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  load(): void {
    forkJoin([this.api.getAll(this.projectId, true), this.api.getTransitions(this.projectId)])
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe(([statuses, transitions]) => {
        this.statuses = sortBugStatuses(statuses);
        this.allowed = new Set(transitions.map((t) => key(t.fromStatusId, t.toStatusId)));
        this.savedAllowed = new Set(this.allowed);
        this.cdr.detectChanges();
      });
  }

  get activeStatuses(): BugStatus[] {
    return this.statuses.filter((s) => !s.archived);
  }

  get restricted(): boolean {
    return this.allowed.size > 0;
  }

  get transitionsChanged(): boolean {
    return this.allowed.size !== this.savedAllowed.size || [...this.allowed].some((k) => !this.savedAllowed.has(k));
  }

  /** Statuses a bug could get stuck in: with rules, an open status with no way out. */
  get deadEnds(): BugStatus[] {
    if (!this.restricted) {
      return [];
    }
    return this.activeStatuses.filter((s) => s.category !== 'CLOSED'
      && !this.activeStatuses.some((t) => this.allowed.has(key(s.id, t.id))));
  }

  isAllowed(from: BugStatus, to: BugStatus): boolean {
    return this.allowed.has(key(from.id, to.id));
  }

  toggle(from: BugStatus, to: BugStatus): void {
    const k = key(from.id, to.id);
    if (!this.allowed.delete(k)) {
      this.allowed.add(k);
    }
    this.allowed = new Set(this.allowed);
  }

  allowAny(): void {
    this.allowed = new Set();
  }

  saveTransitions(): void {
    const transitions: BugStatusTransition[] = [...this.allowed].map((k) => {
      const [fromStatusId, toStatusId] = k.split('|');
      return { fromStatusId, toStatusId };
    });
    this.reloadAfter(this.api.saveTransitions(this.projectId, transitions));
  }

  add(): void {
    const name = this.newName.trim();
    if (!name) {
      return;
    }
    this.reloadAfter(this.api.create(this.projectId, { name, color: this.newColor, category: this.newCategory }),
      () => this.newName = '');
  }

  rename(status: BugStatus, value: string): void {
    const name = value.trim();
    if (!name || name === status.name) {
      this.load();
      return;
    }
    this.save(status, { name });
  }

  save(status: BugStatus, change: { name?: string; color?: string; archived?: boolean }): void {
    this.reloadAfter(this.api.update(this.projectId, status.id, change));
  }

  /** Each status's position becomes its place in the list; only the ones that moved are saved. */
  drop(event: CdkDragDrop<BugStatus[]>): void {
    moveItemInArray(this.statuses, event.previousIndex, event.currentIndex);
    const moved = this.statuses
      .map((s, position) => ({ s, position }))
      .filter(({ s, position }) => s.position !== position);
    if (moved.length) {
      this.reloadAfter(forkJoin(moved.map(({ s, position }) => this.api.update(this.projectId, s.id, { position }))));
    }
  }

  /** Reloads either way: on a refusal (a duplicate name, the last status of a category) the page snaps back. */
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

function key(from: string, to: string): string {
  return `${from}|${to}`;
}
