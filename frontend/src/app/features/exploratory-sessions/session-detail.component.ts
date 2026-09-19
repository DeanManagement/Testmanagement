import { ChangeDetectorRef, Component, DestroyRef, inject, OnDestroy, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { LowerCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { concatMap, of } from 'rxjs';
import { take } from 'rxjs/operators';
import { ExploratorySessionApiService } from '../../core/services/exploratory-session-api.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import {
  ExploratorySession,
  SESSION_NOTE_TYPES,
  SessionNote,
  SessionNoteType,
} from '../../shared/models/exploratory-session.model';
import { AuthImagePipe } from '../../shared/pipes/auth-image.pipe';
import { EnlargeImageDirective } from '../../shared/components/image-viewer/enlarge-image.directive';
import { LocalizedDatePipe } from '../../shared/pipes/localized-date.pipe';
import { bugPrefillFromNote, elapsedMinutes, noteTypeForShortcut, timeboxProgress } from './session-time';

/** The clock only needs minute precision; a slower tick would lag visibly. */
const CLOCK_TICK_MS = 15_000;

/**
 * Running an exploratory session (PRD-034): the charter, the clock against the time box, a
 * one-line note input (Enter saves, Alt+1..4 picks the type, paste attaches a screenshot) and
 * the log, newest first. Every note is saved on Enter, so closing the tab loses nothing.
 */
@Component({
  selector: 'app-session-detail',
  standalone: true,
  imports: [FormsModule, LowerCasePipe, RouterLink, MatButtonModule, MatButtonToggleModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatProgressBarModule, MatTooltipModule, TranslateModule, AuthImagePipe, EnlargeImageDirective,
    LocalizedDatePipe],
  templateUrl: './session-detail.component.html',
  styleUrl: './session-detail.component.scss',
})
export class SessionDetailComponent implements OnInit, OnDestroy {
  private readonly api = inject(ExploratorySessionApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly dialog = inject(MatDialog);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly noteTypes = SESSION_NOTE_TYPES;
  projectId = '';
  sessionId = '';
  session: ExploratorySession | null = null;
  now = new Date();
  noteType: SessionNoteType = 'NOTE';
  noteBody = '';
  pendingImage: File | null = null;
  summary = '';
  saving = false;
  private clock?: ReturnType<typeof setInterval>;

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.sessionId = this.route.snapshot.paramMap.get('sessionId') ?? '';
    this.load();
    this.clock = setInterval(() => {
      this.now = new Date();
      this.cdr.detectChanges();
    }, CLOCK_TICK_MS);
  }

  ngOnDestroy(): void {
    clearInterval(this.clock);
  }

  load(): void {
    this.api.get(this.projectId, this.sessionId).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((session) => this.show(session));
  }

  get elapsed(): number {
    return this.session ? elapsedMinutes(this.session.startedAt, this.session.endedAt, this.now) : 0;
  }

  get progress(): { percent: number; overtime: boolean } {
    return timeboxProgress(this.elapsed, this.session?.timeboxMinutes ?? 0);
  }

  /** Running, or completed less than a day ago (late debrief notes). The server enforces it too. */
  get acceptsNotes(): boolean {
    if (!this.session) {
      return false;
    }
    if (this.session.status === 'IN_PROGRESS') {
      return true;
    }
    const endedAt = this.session.endedAt ? new Date(this.session.endedAt).getTime() : 0;
    return this.session.status === 'COMPLETED' && this.now.getTime() - endedAt < 24 * 60 * 60 * 1000;
  }

  start(): void {
    this.act(this.api.start(this.projectId, this.sessionId));
  }

  complete(): void {
    this.act(this.api.complete(this.projectId, this.sessionId, this.summary.trim() || undefined));
  }

  abort(): void {
    this.act(this.api.abort(this.projectId, this.sessionId, this.summary.trim() || undefined));
  }

  saveSummary(): void {
    this.act(this.api.update(this.projectId, this.sessionId, { summary: this.summary }));
  }

  onNoteKeydown(event: KeyboardEvent): void {
    const type = noteTypeForShortcut(event);
    if (type) {
      event.preventDefault();
      this.noteType = type;
      return;
    }
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.addNote();
    }
  }

  /** Pasting an image (e.g. a screenshot from the clipboard) attaches it to the next note. */
  onNotePaste(event: ClipboardEvent): void {
    const image = Array.from(event.clipboardData?.files ?? []).find((file) => file.type.startsWith('image/'));
    if (image) {
      event.preventDefault();
      this.pendingImage = image;
    }
  }

  onImagePicked(event: Event): void {
    this.pendingImage = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  addNote(): void {
    const body = this.noteBody.trim();
    if (!body || this.saving) {
      return;
    }
    this.saving = true;
    const image = this.pendingImage;
    this.api.addNote(this.projectId, this.sessionId, this.noteType, body).pipe(
      concatMap((note) => image ? this.api.uploadImage(this.projectId, this.sessionId, note.id, image) : of(undefined)),
      take(1),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe({
      next: () => {
        this.noteBody = '';
        this.pendingImage = null;
        this.saving = false;
        this.load();
      },
      // The note may be saved even if its image was refused; reload so the log shows what exists.
      error: () => {
        this.saving = false;
        this.load();
      },
    });
  }

  deleteNote(note: SessionNote): void {
    const data: ConfirmDialogData = { titleKey: 'common.delete', messageKey: 'session.deleteNoteConfirm', danger: true };
    this.dialog.open(ConfirmDialogComponent, { data }).afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (confirmed) {
          this.api.deleteNote(this.projectId, this.sessionId, note.id).pipe(take(1), takeUntilDestroyed(this.destroyRef))
            .subscribe(() => this.load());
        }
      });
  }

  fileBug(note: SessionNote): void {
    const prefill = bugPrefillFromNote(note.body);
    this.router.navigate(['/projects', this.projectId, 'bug-reports', 'new'], {
      queryParams: {
        title: prefill.title,
        description: prefill.description,
        exploratorySessionId: this.sessionId,
        environment: this.session?.environment ?? undefined,
      },
    });
  }

  imageUrl(note: SessionNote): string {
    return this.api.imageUrl(this.projectId, this.sessionId, note.id);
  }

  private act(action: ReturnType<ExploratorySessionApiService['start']>): void {
    this.saving = true;
    action.pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (session) => {
        this.saving = false;
        this.show(session);
      },
      error: () => {
        this.saving = false;
        this.cdr.detectChanges();
      },
    });
  }

  private show(session: ExploratorySession): void {
    this.session = session;
    this.summary = session.summary ?? '';
    this.now = new Date();
    this.cdr.detectChanges();
  }
}
