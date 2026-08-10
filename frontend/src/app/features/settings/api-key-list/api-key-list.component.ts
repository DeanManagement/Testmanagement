import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Store } from '@ngrx/store';
import { AsyncPipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { Actions, ofType } from '@ngrx/effects';
import { take } from 'rxjs/operators';
import { ApiKeyActions } from '../../../store/api-key/api-key.actions';
import { selectAllApiKeys, selectApiKeysLoading } from '../../../store/api-key/api-key.selectors';
import { CreateApiKeyDialogComponent } from '../create-api-key-dialog/create-api-key-dialog.component';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';
import { ApiKey } from '../../../shared/models/api-key.model';
import {
  ConfirmDialogComponent,
  ConfirmDialogData,
} from '../../../shared/components/confirm-dialog/confirm-dialog.component';

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
    MatTooltipModule,
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
  displayedColumns = ['name', 'project', 'role', 'keyPrefix', 'createdAt', 'rotatedAt', 'lastUsedAt', 'status', 'actions'];

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

  /**
   * Rotation is not destructive in the sense that nothing is lost — the key, its project, its role
   * and everything it has written stay — but the old secret dies immediately, which will break a
   * pipeline mid-run if nobody is expecting it. Hence the confirmation, and the warning wording.
   */
  rotateKey(key: ApiKey): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          titleKey: 'settings.apiKeys.rotateTitle',
          messageKey: 'settings.apiKeys.rotateMessage',
          messageParams: { name: key.name },
          secondaryMessageKey: 'settings.apiKeys.rotateWarning',
          danger: true,
        } satisfies ConfirmDialogData,
      })
      .afterClosed()
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((confirmed) => {
        if (!confirmed) {
          return;
        }
        this.store.dispatch(ApiKeyActions.rotateApiKey({ id: key.id }));

        // The new secret is shown once, in the same dialog used after creation — so the operator
        // also gets the ready-to-paste MCP client config with the new key already in it.
        this.actions$
          .pipe(ofType(ApiKeyActions.rotateApiKeySuccess), take(1), takeUntilDestroyed(this.destroyRef))
          .subscribe(({ created }) => {
            const dialogRef = this.dialog.open(CreateApiKeyDialogComponent, { disableClose: true });
            dialogRef.componentInstance.setCreatedKey(created);
          });
      });
  }

  revokeKey(id: string): void {
    this.store.dispatch(ApiKeyActions.revokeApiKey({ id }));
  }
}
