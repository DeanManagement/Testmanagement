import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LowerCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { take } from 'rxjs/operators';
import { ExploratorySessionApiService } from '../../core/services/exploratory-session-api.service';
import { CreateSessionRequest, ExploratorySession } from '../../shared/models/exploratory-session.model';
import { TestRunStatus } from '../../shared/models/test-run.model';
import { elapsedMinutes } from './session-time';
import { SessionFormDialogComponent, SessionFormDialogData } from './session-form-dialog.component';

/** A project's exploratory sessions, newest first, filterable by status (PRD-034). */
@Component({
  selector: 'app-session-list',
  standalone: true,
  imports: [FormsModule, LowerCasePipe, RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule,
    MatProgressSpinnerModule, MatSelectModule, TranslateModule],
  templateUrl: './session-list.component.html',
  styleUrl: './session-list.component.scss',
})
export class SessionListComponent implements OnInit {
  private readonly api = inject(ExploratorySessionApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly statuses: TestRunStatus[] = ['PLANNED', 'IN_PROGRESS', 'COMPLETED', 'ABORTED'];
  projectId = '';
  statusFilter: TestRunStatus | '' = '';
  sessions: ExploratorySession[] = [];
  loading = false;

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api.list(this.projectId, this.statusFilter || undefined).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.sessions = page.content;
          this.loading = false;
          this.cdr.detectChanges();
        },
        error: () => {
          this.loading = false;
          this.cdr.detectChanges();
        },
      });
  }

  elapsed(session: ExploratorySession): number {
    return elapsedMinutes(session.startedAt, session.endedAt, new Date());
  }

  openCreate(): void {
    const data: SessionFormDialogData = { projectId: this.projectId };
    this.dialog.open(SessionFormDialogComponent, { data }).afterClosed()
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((request?: CreateSessionRequest) => {
        if (request) {
          this.api.create(this.projectId, request).pipe(take(1), takeUntilDestroyed(this.destroyRef))
            .subscribe((created) => this.router.navigate(['/projects', this.projectId, 'exploratory-sessions', created.id]));
        }
      });
  }
}
