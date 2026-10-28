import { Component, DestroyRef, inject, Input, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { take } from 'rxjs/operators';
import { RequirementApiService } from '../../../core/services/requirement-api.service';
import { Requirement } from '../../../shared/models/requirement.model';

/** Max requirements offered in the link picker; matches the backend's max page size. */
const PICKER_PAGE_SIZE = 200;

/**
 * The requirements a test case proves, linked and unlinked from the case's side (TM08). The
 * requirements page does the same from the other side; both write the same link.
 */
@Component({
  selector: 'app-test-case-requirements',
  standalone: true,
  imports: [RouterLink, FormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatSelectModule,
    MatTooltipModule, TranslateModule],
  template: `
    <div class="linked" data-test-id="test-case-requirements">
      @for (requirement of linked(); track requirement.id) {
        <span class="requirement-chip" [attr.data-test-id]="'test-case-requirement-' + requirement.externalId">
          <a [routerLink]="['/projects', projectId, 'requirements']">
            <strong>{{ requirement.externalId }}</strong> {{ requirement.title }}
          </a>
          @if (canEdit) {
            <button mat-icon-button type="button" (click)="unlink(requirement)"
                    [matTooltip]="'requirement.unlink' | translate"
                    [attr.data-test-id]="'test-case-requirement-unlink-' + requirement.externalId">
              <mat-icon>link_off</mat-icon>
            </button>
          }
        </span>
      } @empty {
        <span class="none" data-test-id="test-case-requirements-empty">{{ 'testCase.requirements.none' | translate }}</span>
      }
    </div>
    @if (canEdit) {
      @if (picking()) {
        <div class="picker">
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>{{ 'testCase.requirements.choose' | translate }}</mat-label>
            <mat-select [(ngModel)]="selection" data-test-id="test-case-requirement-select">
              @for (requirement of available(); track requirement.id) {
                <mat-option [value]="requirement.id">{{ requirement.externalId }} — {{ requirement.title }}</mat-option>
              }
            </mat-select>
          </mat-form-field>
          <button mat-flat-button type="button" [disabled]="!selection" (click)="link()"
                  data-test-id="test-case-requirement-link-confirm">{{ 'requirement.link' | translate }}</button>
          <button mat-button type="button" (click)="picking.set(false)">{{ 'common.cancel' | translate }}</button>
        </div>
      } @else {
        <button mat-stroked-button type="button" (click)="openPicker()" data-test-id="test-case-requirement-link-btn">
          <mat-icon>add_link</mat-icon> {{ 'testCase.requirements.link' | translate }}
        </button>
      }
    }
  `,
  styles: [`
    .linked { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
    .requirement-chip { display: inline-flex; align-items: center; gap: 2px; padding: 0 0 0 10px;
      border: 1px solid var(--tm-border); border-radius: 16px; font-size: 13px; }
    .requirement-chip a { color: inherit; text-decoration: none; }
    .none { color: var(--tm-text-secondary); font-size: 13px; }
    .picker { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
    .picker mat-form-field { min-width: 280px; }
  `],
})
export class TestCaseRequirementsComponent implements OnInit {
  private readonly api = inject(RequirementApiService);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) projectId!: string;
  @Input({ required: true }) testCaseId!: string;
  @Input() canEdit = false;

  readonly linked = signal<Requirement[]>([]);
  readonly available = signal<Requirement[]>([]);
  readonly picking = signal(false);
  selection = '';

  ngOnInit(): void {
    this.reload();
  }

  openPicker(): void {
    this.selection = '';
    this.picking.set(true);
    this.api.getAll(this.projectId, 0, PICKER_PAGE_SIZE).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((page) => {
        const linkedIds = new Set(this.linked().map((r) => r.id));
        this.available.set(page.content.filter((r) => !linkedIds.has(r.id)));
      });
  }

  link(): void {
    this.api.linkTestCase(this.projectId, this.selection, this.testCaseId)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.picking.set(false);
        this.reload();
      });
  }

  unlink(requirement: Requirement): void {
    this.api.unlinkTestCase(this.projectId, requirement.id, this.testCaseId)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload());
  }

  private reload(): void {
    this.api.getForTestCase(this.projectId, this.testCaseId).pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (requirements) => this.linked.set(requirements), error: () => undefined });
  }
}
