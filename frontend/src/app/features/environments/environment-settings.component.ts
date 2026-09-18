import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { forkJoin } from 'rxjs';
import { take } from 'rxjs/operators';
import { EnvironmentApiService } from '../../core/services/environment-api.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import { ProjectEnvironment } from '../../shared/models/environment.model';
import { sortOrderChanges } from './environment-order';
import { MergeEnvironmentDialogComponent, MergeEnvironmentDialogData } from './merge-environment-dialog.component';

/** Curate the project's environment catalogue (PRD-032): order, rename, archive, merge, delete. */
@Component({
  selector: 'app-environment-settings',
  standalone: true,
  imports: [
    RouterLink,
    FormsModule,
    DragDropModule,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
    MatTooltipModule,
    TranslateModule,
  ],
  templateUrl: './environment-settings.component.html',
  styleUrl: './environment-settings.component.scss',
})
export class EnvironmentSettingsComponent implements OnInit {
  private readonly api = inject(EnvironmentApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  projectId = '';
  environments: ProjectEnvironment[] = [];
  loading = false;
  newName = '';
  editingId: string | null = null;
  editingName = '';

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api.getAll(this.projectId).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (environments) => {
        this.environments = environments;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  add(): void {
    const name = this.newName.trim();
    if (!name) {
      return;
    }
    this.api.create(this.projectId, name).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.newName = '';
      this.load();
    });
  }

  drop(event: CdkDragDrop<ProjectEnvironment[]>): void {
    moveItemInArray(this.environments, event.previousIndex, event.currentIndex);
    const changes = sortOrderChanges(this.environments);
    if (changes.length === 0) {
      return;
    }
    forkJoin(changes.map((change) => this.api.update(this.projectId, change.id, { sortOrder: change.sortOrder })))
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      // Reload either way: on failure the list snaps back to what the server has.
      .subscribe({ next: () => this.load(), error: () => this.load() });
  }

  startRename(environment: ProjectEnvironment): void {
    this.editingId = environment.id;
    this.editingName = environment.name;
  }

  cancelRename(): void {
    this.editingId = null;
  }

  saveRename(environment: ProjectEnvironment): void {
    const name = this.editingName.trim();
    if (!name || name === environment.name) {
      this.cancelRename();
      return;
    }
    this.api.update(this.projectId, environment.id, { name }).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.editingId = null;
        this.load();
      });
  }

  setArchived(environment: ProjectEnvironment, archived: boolean): void {
    this.api.update(this.projectId, environment.id, { archived }).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: () => this.load(), error: () => this.load() });
  }

  isInUse(environment: ProjectEnvironment): boolean {
    return environment.runCount + environment.bugCount > 0;
  }

  merge(source: ProjectEnvironment): void {
    const data: MergeEnvironmentDialogData = {
      source,
      candidates: this.environments.filter((environment) => environment.id !== source.id),
    };
    this.dialog.open(MergeEnvironmentDialogComponent, { data }).afterClosed()
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((targetId?: string) => {
        if (targetId) {
          this.api.merge(this.projectId, source.id, targetId).pipe(take(1), takeUntilDestroyed(this.destroyRef))
            .subscribe(() => this.load());
        }
      });
  }

  remove(environment: ProjectEnvironment): void {
    const data: ConfirmDialogData = {
      titleKey: 'common.delete',
      messageKey: 'environment.deleteConfirm',
      messageParams: { name: environment.name },
      danger: true,
    };
    this.dialog.open(ConfirmDialogComponent, { data }).afterClosed()
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (confirmed) {
          this.api.delete(this.projectId, environment.id).pipe(take(1), takeUntilDestroyed(this.destroyRef))
            .subscribe(() => this.load());
        }
      });
  }
}
