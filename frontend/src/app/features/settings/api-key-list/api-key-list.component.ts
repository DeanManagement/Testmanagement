import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Store } from '@ngrx/store';
import { AsyncPipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { Actions, ofType } from '@ngrx/effects';
import { take } from 'rxjs/operators';
import { ApiKeyActions } from '../../../store/api-key/api-key.actions';
import { selectAllApiKeys, selectApiKeysLoading } from '../../../store/api-key/api-key.selectors';
import { CreateApiKeyDialogComponent } from '../create-api-key-dialog/create-api-key-dialog.component';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';

@Component({
  selector: 'app-api-key-list',
  standalone: true,
  imports: [
    AsyncPipe,
    LocalizedDatePipe,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './api-key-list.component.html',
  styleUrl: './api-key-list.component.scss',
})
export class ApiKeyListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly dialog = inject(MatDialog);
  private readonly actions$ = inject(Actions);
  private readonly destroyRef = inject(DestroyRef);

  apiKeys$ = this.store.select(selectAllApiKeys);
  loading$ = this.store.select(selectApiKeysLoading);
  displayedColumns = ['name', 'project', 'role', 'keyPrefix', 'createdAt', 'lastUsedAt', 'status', 'actions'];

  ngOnInit(): void {
    this.store.dispatch(ApiKeyActions.loadApiKeys());
  }

  openCreateDialog(): void {
    const dialogRef = this.dialog.open(CreateApiKeyDialogComponent, {
      disableClose: true,
    });

    dialogRef.afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe((result) => {
      if (result?.action === 'create') {
        this.store.dispatch(ApiKeyActions.createApiKey({
          request: { name: result.name, projectId: result.projectId, role: result.role },
        }));

        this.actions$.pipe(
          ofType(ApiKeyActions.createApiKeySuccess),
          take(1),
          takeUntilDestroyed(this.destroyRef)
        ).subscribe(({ created }) => {
          const newDialogRef = this.dialog.open(CreateApiKeyDialogComponent, {
            disableClose: true,
          });
          newDialogRef.componentInstance.setCreatedKey(created);
        });
      }
    });
  }

  revokeKey(id: string): void {
    this.store.dispatch(ApiKeyActions.revokeApiKey({ id }));
  }
}
