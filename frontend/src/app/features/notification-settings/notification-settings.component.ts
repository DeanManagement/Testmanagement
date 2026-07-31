import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { take } from 'rxjs/operators';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { NotificationApiService } from '../../core/services/notification-api.service';
import { NOTIFICATION_ACTIONS, NotificationAction, NotificationPreference } from '../../shared/models/notification.model';

@Component({
  selector: 'app-notification-settings',
  standalone: true,
  imports: [FormsModule, RouterLink, MatButtonModule, MatIconModule, MatSlideToggleModule, TranslateModule],
  templateUrl: './notification-settings.component.html',
  styleUrl: './notification-settings.component.scss',
})
export class NotificationSettingsComponent implements OnInit {
  private readonly api = inject(NotificationApiService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly actions = NOTIFICATION_ACTIONS;
  prefs: Record<NotificationAction, NotificationPreference> = {} as Record<NotificationAction, NotificationPreference>;
  saving = false;

  ngOnInit(): void {
    // Defaults: in-app on, email off.
    for (const action of this.actions) {
      this.prefs[action] = { action, inApp: true, email: false };
    }
    this.api.getPreferences().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe((saved) => {
      for (const pref of saved) {
        this.prefs[pref.action] = pref;
      }
      this.cdr.markForCheck();
    });
  }

  save(): void {
    this.saving = true;
    this.api.updatePreferences(this.actions.map((a) => this.prefs[a])).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.saving = false;
        this.snackBar.open(this.translate.instant('notification.prefs.saved'), 'OK', { duration: 4000 });
        this.cdr.markForCheck();
      },
      error: () => {
        this.saving = false;
        this.cdr.markForCheck();
      },
    });
  }
}
