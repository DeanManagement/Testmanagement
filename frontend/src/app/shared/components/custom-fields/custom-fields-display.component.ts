import { Component, computed, input } from '@angular/core';
import { CustomFieldValues } from '../../models/custom-field.model';
import { displayValue } from './custom-field-values';

/**
 * Read-only custom field values for a detail page (PRD-035 §3.9). The API returns only fields
 * that hold a value, in display order, so an archived field shows exactly when it has one.
 */
@Component({
  selector: 'app-custom-fields-display',
  standalone: true,
  template: `
    @if (entries().length > 0) {
      <dl class="custom-fields" data-test-id="custom-fields-display">
        @for (entry of entries(); track entry.name) {
          <div class="custom-field">
            <dt>{{ entry.name }}</dt>
            <dd>{{ entry.value }}</dd>
          </div>
        }
      </dl>
    }
  `,
  styles: [`
    .custom-fields { display: grid; grid-template-columns: repeat(auto-fill, minmax(12rem, 1fr)); gap: 0.75rem 1.5rem; margin: 1rem 0 0; }
    dt { font-size: 0.75rem; color: var(--tm-text-secondary); }
    dd { margin: 0.125rem 0 0; overflow-wrap: anywhere; }
  `],
})
export class CustomFieldsDisplayComponent {
  readonly values = input<CustomFieldValues | null | undefined>();

  readonly entries = computed(() =>
    Object.entries(this.values() ?? {}).map(([name, value]) => ({ name, value: displayValue(value) })));
}
