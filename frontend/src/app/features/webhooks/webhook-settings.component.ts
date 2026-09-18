import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { take } from 'rxjs/operators';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTableModule } from '@angular/material/table';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar } from '@angular/material/snack-bar';
import { DatePipe } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { WebhookApiService } from '../../core/services/webhook-api.service';
import { ConfirmDialogComponent, ConfirmDialogData } from '../../shared/components/confirm-dialog/confirm-dialog.component';
import {
  ALL_WEBHOOK_EVENTS,
  ALL_WEBHOOK_FORMATS,
  Webhook,
  WebhookDelivery,
  WebhookEventType,
  WebhookFormat,
} from '../../shared/models/webhook.model';
import {
  eventsAfterFormatChange,
  FORMAT_SETUP_DOCS,
  isChatFormat,
  isEventAllowed,
  isWebhookFormValid,
} from './webhook-form';

@Component({
  selector: 'app-webhook-settings',
  standalone: true,
  imports: [
    RouterLink,
    FormsModule,
    DatePipe,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatSlideToggleModule,
    MatFormFieldModule,
    MatInputModule,
    MatCheckboxModule,
    MatTableModule,
    MatSelectModule,
    MatTooltipModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './webhook-settings.component.html',
  styleUrl: './webhook-settings.component.scss',
})
export class WebhookSettingsComponent implements OnInit {
  private readonly api = inject(WebhookApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  projectId = '';
  webhooks: Webhook[] = [];
  loading = false;
  readonly allEvents = ALL_WEBHOOK_EVENTS;
  readonly allFormats = ALL_WEBHOOK_FORMATS;
  readonly setupDocs = FORMAT_SETUP_DOCS;
  readonly isChatFormat = isChatFormat;
  readonly isEventAllowed = isEventAllowed;
  deliveryColumns = ['event', 'status', 'attempt', 'createdAt'];

  // Inline create/edit form state
  formOpen = false;
  editingId: string | null = null;
  formUrl = '';
  formSecret = '';
  formEvents = new Set<WebhookEventType>();
  formActive = true;
  formFormat: WebhookFormat = 'GENERIC';
  /** Existing chat webhooks show their masked URL until the admin chooses to replace it. */
  formEditingUrl = true;
  formMaskedUrl = '';

  // Delivery log state
  expandedWebhookId: string | null = null;
  deliveries: WebhookDelivery[] = [];
  deliveriesLoading = false;

  ngOnInit(): void {
    this.projectId = this.route.snapshot.paramMap.get('id') ?? '';
    this.load();
  }

  load(): void {
    this.loading = true;
    this.api.getAll(this.projectId).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (webhooks) => {
        this.webhooks = webhooks;
        this.loading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.loading = false;
        this.cdr.detectChanges();
      },
    });
  }

  openCreate(): void {
    this.editingId = null;
    this.formUrl = '';
    this.formSecret = '';
    this.formEvents = new Set();
    this.formActive = true;
    this.formFormat = 'GENERIC';
    this.formEditingUrl = true;
    this.formMaskedUrl = '';
    this.formOpen = true;
  }

  openEdit(webhook: Webhook): void {
    this.editingId = webhook.id;
    this.formFormat = webhook.format;
    this.formEditingUrl = !isChatFormat(webhook.format);
    this.formMaskedUrl = this.formEditingUrl ? '' : webhook.url;
    this.formUrl = this.formEditingUrl ? webhook.url : '';
    this.formSecret = '';
    this.formEvents = new Set(webhook.events);
    this.formActive = webhook.active;
    this.formOpen = true;
  }

  changeFormat(format: WebhookFormat): void {
    this.formFormat = format;
    this.formEvents = eventsAfterFormatChange(format, this.formEvents, this.editingId === null);
  }

  replaceUrl(): void {
    this.formEditingUrl = true;
    this.formUrl = '';
  }

  cancelForm(): void {
    this.formOpen = false;
  }

  toggleEvent(event: WebhookEventType, checked: boolean): void {
    if (checked) {
      this.formEvents.add(event);
    } else {
      this.formEvents.delete(event);
    }
  }

  get formValid(): boolean {
    return isWebhookFormValid({
      format: this.formFormat,
      isNew: this.editingId === null,
      editingUrl: this.formEditingUrl,
      url: this.formUrl,
      secret: this.formSecret,
      events: this.formEvents,
    });
  }

  save(): void {
    const events = Array.from(this.formEvents);
    if (this.editingId) {
      this.api.update(this.projectId, this.editingId, {
        url: this.formEditingUrl ? this.formUrl : undefined,
        secret: this.formSecret.trim() ? this.formSecret : undefined,
        events,
        active: this.formActive,
        format: this.formFormat,
      }).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(() => {
        this.formOpen = false;
        this.load();
      });
    } else {
      this.api.create(this.projectId, {
        url: this.formUrl,
        secret: this.formSecret.trim() ? this.formSecret : undefined,
        events,
        active: this.formActive,
        format: this.formFormat,
      }).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(() => {
        this.formOpen = false;
        this.load();
      });
    }
  }

  toggleActive(webhook: Webhook, active: boolean): void {
    // No url: a chat webhook's is masked, and an omitted url keeps the stored one.
    this.api.update(this.projectId, webhook.id, {
      events: webhook.events,
      active,
    }).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
  }

  test(webhook: Webhook): void {
    this.api.test(this.projectId, webhook.id).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (delivery) => {
        const key = delivery.success ? 'webhook.testSuccess' : 'webhook.testFailed';
        this.snackBar.open(
          this.translate.instant(key, { status: delivery.responseStatus ?? '-' }),
          'OK',
          { duration: 5000 },
        );
        if (this.expandedWebhookId === webhook.id) {
          this.viewDeliveries(webhook);
        }
      },
    });
  }

  remove(webhook: Webhook): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: {
        titleKey: 'common.delete',
        messageKey: 'webhook.deleteConfirm',
        danger: true,
      } as ConfirmDialogData,
    });
    dialogRef.afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe((confirmed) => {
      if (confirmed) {
        this.api.delete(this.projectId, webhook.id).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
      }
    });
  }

  viewDeliveries(webhook: Webhook): void {
    if (this.expandedWebhookId === webhook.id) {
      this.expandedWebhookId = null;
      return;
    }
    this.expandedWebhookId = webhook.id;
    this.deliveriesLoading = true;
    this.deliveries = [];
    this.api.getDeliveries(this.projectId, webhook.id).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (page) => {
        this.deliveries = page.content;
        this.deliveriesLoading = false;
        this.cdr.detectChanges();
      },
      error: () => {
        this.deliveriesLoading = false;
        this.cdr.detectChanges();
      },
    });
  }

  deliveryStatusLabel(delivery: WebhookDelivery): string {
    if (delivery.success === true) return 'webhook.delivered';
    if (delivery.success === false) return 'webhook.failed';
    return 'webhook.pending';
  }
}
