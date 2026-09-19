import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { LowerCasePipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { Subject } from 'rxjs';
import { debounceTime, map, startWith, switchMap } from 'rxjs/operators';
import { BugReportApiService } from '../../../core/services/bug-report-api.service';
import { BugReport, OPEN_BUG_STATUSES } from '../../../shared/models/bug-report.model';

const SEARCH_DEBOUNCE_MS = 250;
const RESULTS = 20;
const OPEN = new Set(OPEN_BUG_STATUSES);

export interface LinkBugDialogData {
  projectId: string;
  /** Bugs already on this result, so none is offered twice. */
  excludeIds: string[];
}

/**
 * Picks an existing bug to link to a result or step (PRD-047): search by key or title, open bugs
 * first. Closes with the chosen bug, or nothing.
 */
@Component({
  selector: 'app-link-bug-dialog',
  standalone: true,
  imports: [FormsModule, LowerCasePipe, MatButtonModule, MatDialogModule, MatFormFieldModule, MatIconModule,
    MatInputModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'bugReport.link.title' | translate }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'bugReport.list.search' | translate }}</mat-label>
        <mat-icon matPrefix>search</mat-icon>
        <input matInput [ngModel]="term" (ngModelChange)="onSearch($event)" cdkFocusInitial data-test-id="link-bug-search" />
      </mat-form-field>
      <ul class="bug-options" data-test-id="link-bug-options">
        @for (bug of bugs(); track bug.id) {
          <li>
            <button mat-button type="button" class="bug-option" (click)="pick(bug)" [attr.data-test-id]="'link-bug-' + bug.key">
              <span class="bug-key">{{ bug.key }}</span>
              <span class="bug-title">{{ bug.title }}</span>
              <span class="badge badge--{{ bug.status | lowercase }} badge--small">{{ 'bugReport.status.' + bug.status | translate }}</span>
            </button>
          </li>
        } @empty {
          <li class="none">{{ 'bugReport.link.none' | translate }}</li>
        }
      </ul>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'common.cancel' | translate }}</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width { width: 100%; }
    mat-dialog-content { min-width: min(90vw, 480px); }
    .bug-options { list-style: none; margin: 0; padding: 0; max-height: 50vh; overflow: auto; }
    .bug-option { width: 100%; justify-content: flex-start; text-align: left; }
    .bug-key { font-weight: 600; margin-right: 8px; }
    .bug-title { flex: 1; margin-right: 8px; overflow: hidden; text-overflow: ellipsis; }
    .none { color: var(--tm-text-secondary); padding: 8px 0; }
    .badge--small { font-size: 10px; padding: 1px 6px; }
  `],
})
export class LinkBugDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<LinkBugDialogComponent>);
  private readonly api = inject(BugReportApiService);
  readonly data: LinkBugDialogData = inject(MAT_DIALOG_DATA);

  term = '';
  readonly bugs = signal<BugReport[]>([]);
  private readonly terms = new Subject<string>();

  constructor() {
    this.terms.pipe(
      debounceTime(SEARCH_DEBOUNCE_MS),
      startWith(''),
      switchMap((q) => this.api.getAll(this.data.projectId, { q: q || undefined, size: RESULTS, sort: 'createdAt,desc' })),
      map((page) => openFirst(page.content.filter((bug) => !this.data.excludeIds.includes(bug.id)))),
      takeUntilDestroyed(inject(DestroyRef)),
    ).subscribe((bugs) => this.bugs.set(bugs));
  }

  onSearch(term: string): void {
    this.term = term;
    this.terms.next(term.trim());
  }

  pick(bug: BugReport): void {
    this.dialogRef.close(bug);
  }
}

/** Open bugs before resolved and closed ones, each group keeping the server's order. */
export function openFirst(bugs: BugReport[]): BugReport[] {
  return [...bugs.filter((b) => OPEN.has(b.status)), ...bugs.filter((b) => !OPEN.has(b.status))];
}
