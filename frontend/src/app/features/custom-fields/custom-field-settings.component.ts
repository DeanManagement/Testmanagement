import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { CdkDragDrop, DragDropModule, moveItemInArray } from '@angular/cdk/drag-drop';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Observable, forkJoin } from 'rxjs';
import { take } from 'rxjs/operators';
import { CustomFieldApiService } from '../../core/services/custom-field-api.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import {
  CUSTOM_FIELD_ENTITY_TYPES,
  CustomField,
  CustomFieldEntityType,
  MAX_ACTIVE_CUSTOM_FIELDS,
} from '../../shared/models/custom-field.model';
import { CustomFieldDialogComponent, CustomFieldDialogData } from './custom-field-dialog.component';
import { orderIndexChanges } from './custom-field-options';

const HTTP_CONFLICT = 409;

/** Define a project's custom fields (PRD-035): one list per entity type; add, edit, order, archive, delete. */
@Component({
  selector: 'app-custom-field-settings',
  standalone: true,
  imports: [
    RouterLink,
    DragDropModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatSlideToggleModule,
    MatTooltipModule,
    TranslateModule,
  ],
  templateUrl: './custom-field-settings.component.html',
  styleUrl: './custom-field-settings.component.scss',
})
export class CustomFieldSettingsComponent implements OnInit {
  private readonly api = inject(CustomFieldApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly entityTypes = CUSTOM_FIELD_ENTITY_TYPES;
  readonly maxActive = MAX_ACTIVE_CUSTOM_FIELDS;

  projectId = '';
  loading = false;
  fieldsByType: Record<CustomFieldEntityType, CustomField[]> = { TEST_CASE: [], TEST_RUN: [], BUG_REPORT: [] };

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api.getAll(this.projectId).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (fields) => {
        for (const entityType of this.entityTypes) {
          this.fieldsByType[entityType] = fields.filter((field) => field.entityType === entityType);
        }
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  isFull(entityType: CustomFieldEntityType): boolean {
    return this.fieldsByType[entityType].filter((field) => !field.archived).length >= this.maxActive;
  }

  add(entityType: CustomFieldEntityType): void {
    this.openDialog({ projectId: this.projectId, entityType });
  }

  edit(field: CustomField): void {
    this.openDialog({ projectId: this.projectId, entityType: field.entityType, field });
  }

  drop(entityType: CustomFieldEntityType, event: CdkDragDrop<CustomField[]>): void {
    const fields = this.fieldsByType[entityType];
    moveItemInArray(fields, event.previousIndex, event.currentIndex);
    const changes = orderIndexChanges(fields);
    if (changes.length === 0) {
      return;
    }
    // Reload either way: on failure the list snaps back to what the server has.
    this.run(forkJoin(changes.map((change) =>
      this.api.update(this.projectId, change.id, { orderIndex: change.orderIndex }))));
  }

  setArchived(field: CustomField, archived: boolean): void {
    this.run(archived ? this.api.archive(this.projectId, field.id) : this.api.unarchive(this.projectId, field.id));
  }

  remove(field: CustomField): void {
    this.confirm({ titleKey: 'common.delete', messageKey: 'customField.deleteConfirm', messageParams: { name: field.name }, danger: true },
      () => this.api.delete(this.projectId, field.id).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => this.load(),
        error: (error: HttpErrorResponse) => {
          if (error.status === HTTP_CONFLICT) {
            this.offerForceDelete(field);
          } else {
            this.showError(error);
          }
        },
      }));
  }

  /** The field holds values: deleting it now means discarding them, which needs its own yes. */
  private offerForceDelete(field: CustomField): void {
    this.confirm({
      titleKey: 'customField.forceDelete.title',
      messageKey: 'customField.forceDelete.message',
      messageParams: { name: field.name },
      secondaryMessageKey: 'customField.forceDelete.archiveInstead',
      danger: true,
    }, () => this.run(this.api.forceDelete(this.projectId, field.id)));
  }

  private confirm(data: ConfirmDialogData, onConfirmed: () => void): void {
    this.dialog.open(ConfirmDialogComponent, { data }).afterClosed()
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (confirmed) {
          onConfirmed();
        }
      });
  }

  private openDialog(data: CustomFieldDialogData): void {
    this.dialog.open(CustomFieldDialogComponent, { data }).afterClosed()
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((saved?: boolean) => {
        if (saved) {
          this.load();
        }
      });
  }

  private run(request: Observable<unknown>): void {
    request.pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => this.load(),
      error: (error: HttpErrorResponse) => {
        this.showError(error);
        this.load();
      },
    });
  }

  /** 4xx refusals carry the reason (the cap, a name clash); the error interceptor leaves them to the page. */
  private showError(error: HttpErrorResponse): void {
    this.snackBar.open(error.error?.message ?? error.message, this.translate.instant('common.ok'), {
      duration: 8000,
      panelClass: 'snackbar-error',
    });
  }
}
