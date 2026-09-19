import { Component, inject, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AuditEntityType, AuditEntry, FieldChange } from '../../models/activity.model';
import { activitySentence } from '../../utils/activity-sentence';
import { activityRoute } from '../../utils/activity-link';
import { LocalizedDatePipe } from '../../pipes/localized-date.pipe';

/** Translation prefix for a status value, by the kind of object whose status it is. */
const STATUS_KEYS: Partial<Record<AuditEntityType, string>> = {
  BUG_REPORT: 'bugReport.status.',
  TEST_RUN: 'testRun.status.',
  TEST_PLAN: 'testPlan.status.',
  TEST_CASE: 'testCaseStatus.',
};

const ACTION_ICONS: Record<string, string> = {
  CREATED: 'add_circle',
  UPDATED: 'edit',
  DELETED: 'delete',
  STATUS_CHANGED: 'swap_horiz',
  COMPLETED: 'check_circle',
  REOPENED: 'replay',
  CLONED: 'content_copy',
};

/**
 * One activity entry (PRD-046): the sentence, linking to the object while it exists, the free-text
 * details, and a row per changed field as "Field: old → new".
 */
@Component({
  selector: 'app-activity-entry',
  standalone: true,
  imports: [RouterLink, MatIconModule, TranslateModule, LocalizedDatePipe],
  templateUrl: './activity-entry.component.html',
  styleUrl: './activity-entry.component.scss',
})
export class ActivityEntryComponent {
  private readonly translate = inject(TranslateService);

  @Input({ required: true }) entry!: AuditEntry;
  @Input({ required: true }) projectId!: string;
  /** On an object's own history a link back to that object leads nowhere new, so none is shown. */
  @Input() currentEntityId: string | null = null;

  get icon(): string {
    return ACTION_ICONS[this.entry.action] ?? 'info';
  }

  get sentence(): string {
    return activitySentence(this.translate, {
      actor: this.entry.userDisplayName || this.translate.instant('activity.system'),
      action: this.entry.action,
      entityType: this.entry.entityType,
      entityName: this.entry.entityName,
    });
  }

  get route(): string[] | null {
    if (this.entry.link?.id === this.currentEntityId) return null;
    return activityRoute(this.projectId, this.entry.link);
  }

  fieldLabel(change: FieldChange): string {
    const key = 'activity.fields.' + change.field;
    const label = this.translate.instant(key);
    return label === key ? change.field : label;
  }

  /** Status, priority and resolution values read as the UI names them elsewhere. */
  valueLabel(change: FieldChange, value: string | null): string {
    if (value == null) return '—';
    const prefix = this.valuePrefix(change.field);
    if (!prefix) return value;
    const label = this.translate.instant(prefix + value);
    return label === prefix + value ? value : label;
  }

  private valuePrefix(field: string): string | undefined {
    if (field === 'status') return STATUS_KEYS[this.entry.entityType];
    if (field === 'priority') return 'priority.';
    if (field === 'resolution') return 'bugReport.resolution.';
    return undefined;
  }
}
