import { ChangeDetectorRef, Component, DestroyRef, OnInit, inject, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { take } from 'rxjs/operators';
import { CustomFieldApiService } from '../../../core/services/custom-field-api.service';
import { CustomField, CustomFieldEntityType } from '../../models/custom-field.model';
import {
  CustomFieldFilterState,
  hasOptions,
  isActive,
  isRangeField,
  readFilterState,
  toQueryParams,
} from './custom-field-filter-params';

/**
 * "More filters" for a list page (PRD-035 §3.9): one control per active custom field. The URL's
 * `cf.*` query parameters are the state, like every other list filter, so a filtered list stays
 * shareable; the list page passes them through to the API. Renders nothing without fields.
 */
@Component({
  selector: 'app-custom-field-filters',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatSelectModule, TranslateModule],
  template: `
    @if (fields.length > 0) {
      <button mat-stroked-button type="button" (click)="open = !open" [attr.aria-expanded]="open"
              aria-controls="custom-field-filter-panel" data-test-id="custom-field-filters-toggle">
        <mat-icon>filter_list</mat-icon>
        {{ 'customField.filters.more' | translate }}
        @if (activeCount > 0) {
          <span class="active-count">{{ activeCount }}</span>
        }
      </button>
      @if (open) {
        <div class="filter-panel" id="custom-field-filter-panel" data-test-id="custom-field-filter-panel">
          @for (field of fields; track field.id) {
            @if (hasOptions(field)) {
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>{{ field.name }}</mat-label>
                <mat-select multiple [(ngModel)]="states[field.id].options" (selectionChange)="apply(field)">
                  @for (option of field.options; track option) {
                    <mat-option [value]="option">{{ option }}</mat-option>
                  }
                </mat-select>
              </mat-form-field>
            } @else if (isRangeField(field)) {
              <mat-form-field appearance="outline" subscriptSizing="dynamic" class="range-field">
                <mat-label>{{ 'customField.filters.from' | translate: { name: field.name } }}</mat-label>
                <input matInput [type]="inputType(field)" step="any" [(ngModel)]="states[field.id].min" (change)="apply(field)">
              </mat-form-field>
              <mat-form-field appearance="outline" subscriptSizing="dynamic" class="range-field">
                <mat-label>{{ 'customField.filters.to' | translate: { name: field.name } }}</mat-label>
                <input matInput [type]="inputType(field)" step="any" [(ngModel)]="states[field.id].max" (change)="apply(field)">
              </mat-form-field>
            } @else {
              <mat-form-field appearance="outline" subscriptSizing="dynamic">
                <mat-label>{{ field.name }}</mat-label>
                <input matInput [(ngModel)]="states[field.id].text" (change)="apply(field)">
              </mat-form-field>
            }
          }
          @if (activeCount > 0) {
            <button mat-button type="button" (click)="clearAll()" data-test-id="custom-field-filters-clear">
              {{ 'customField.filters.clear' | translate }}
            </button>
          }
        </div>
      }
    }
  `,
  styles: [`
    :host { display: contents; }
    button { align-self: center; }
    .active-count { margin-left: 0.5rem; padding: 0 0.4rem; border-radius: 999px; font-size: 0.75rem;
                    background: var(--tm-brand-surface); color: #fff; }
    /* order: after every sibling of the host list's filter bar, so opening it never splits that row. */
    .filter-panel { order: 1; flex-basis: 100%; display: flex; flex-wrap: wrap; align-items: center; gap: 0.75rem; }
    .filter-panel mat-form-field { width: 14rem; }
    .filter-panel .range-field { width: 10rem; }
  `],
})
export class CustomFieldFiltersComponent implements OnInit {
  private readonly api = inject(CustomFieldApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly projectId = input.required<string>();
  readonly entityType = input.required<CustomFieldEntityType>();

  readonly hasOptions = hasOptions;
  readonly isRangeField = isRangeField;

  fields: CustomField[] = [];
  states: Record<string, CustomFieldFilterState> = {};
  activeCount = 0;
  open = false;
  private params: ParamMap = this.route.snapshot.queryParamMap;

  ngOnInit(): void {
    this.api.getActive(this.projectId(), this.entityType()).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((fields) => {
        this.fields = fields;
        this.readStates();
        // A shared link with custom field filters opens with them visible.
        this.open = this.activeCount > 0;
      });
    this.route.queryParamMap.pipe(takeUntilDestroyed(this.destroyRef)).subscribe((params) => {
      this.params = params;
      this.readStates();
    });
  }

  inputType(field: CustomField): 'number' | 'date' {
    return field.fieldType === 'NUMBER' ? 'number' : 'date';
  }

  apply(field: CustomField): void {
    this.navigate(toQueryParams(field, this.states[field.id]));
  }

  clearAll(): void {
    const cleared = { options: [], text: '', min: '', max: '' };
    this.navigate(Object.assign({}, ...this.fields.map((field) => toQueryParams(field, cleared))));
  }

  private navigate(queryParams: Record<string, string | string[] | null>): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { ...queryParams, page: null },
      queryParamsHandling: 'merge',
      replaceUrl: true,
    });
  }

  private readStates(): void {
    this.states = Object.fromEntries(this.fields.map((field) => [field.id, readFilterState(field, this.params)]));
    this.activeCount = Object.values(this.states).filter(isActive).length;
    this.cdr.markForCheck();
  }
}
