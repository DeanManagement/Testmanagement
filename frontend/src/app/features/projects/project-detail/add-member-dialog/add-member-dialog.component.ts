import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { take } from 'rxjs/operators';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { UserApiService } from '../../../../core/services/user-api.service';
import { User } from '../../../../shared/models/user.model';
import { ProjectRole } from '../../../../shared/models/project-member.model';

@Component({
  selector: 'app-add-member-dialog',
  standalone: true,
  imports: [
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    TranslateModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ 'project.members.addTitle' | translate }}</h2>
    <mat-dialog-content>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'project.members.selectUser' | translate }}</mat-label>
        <mat-select [(ngModel)]="selectedUserId" required data-test-id="member-user-select">
          @for (user of users; track user.id) {
            <mat-option [value]="user.id">{{ user.displayName }} ({{ user.email }})</mat-option>
          }
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="outline" class="full-width">
        <mat-label>{{ 'project.members.role' | translate }}</mat-label>
        <mat-select [(ngModel)]="selectedRole" required data-test-id="member-role-select">
          <mat-option value="ADMIN">{{ 'project.members.roles.ADMIN' | translate }}</mat-option>
          <mat-option value="TESTER">{{ 'project.members.roles.TESTER' | translate }}</mat-option>
          <mat-option value="VIEWER">{{ 'project.members.roles.VIEWER' | translate }}</mat-option>
        </mat-select>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="onCancel()">{{ 'common.cancel' | translate }}</button>
      <button mat-flat-button color="primary"
              [disabled]="!selectedUserId || !selectedRole"
              (click)="onAdd()" data-test-id="member-add-confirm">
        {{ 'project.members.add' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [`
    .full-width { width: 100%; }
    mat-dialog-content { display: flex; flex-direction: column; gap: 8px; min-width: min(90vw, 450px); }
  `],
})
export class AddMemberDialogComponent implements OnInit {
  private readonly dialogRef = inject(MatDialogRef<AddMemberDialogComponent>);
  private readonly userApi = inject(UserApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  users: User[] = [];
  selectedUserId = '';
  selectedRole: ProjectRole = 'TESTER';

  ngOnInit(): void {
    this.userApi.getAll().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe((users) => {
      this.users = users;
      this.cdr.detectChanges();
    });
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onAdd(): void {
    this.dialogRef.close({
      userId: this.selectedUserId,
      role: this.selectedRole,
    });
  }
}
