import { Component, DestroyRef, EventEmitter, inject, Input, OnInit, Output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { take } from 'rxjs/operators';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { TestCase } from '../../../shared/models/test-case.model';
import { TestCaseLink, TestCaseLinkType } from '../../../shared/models/test-case-link.model';

/** Matches offered per search in the case picker. */
const PICKER_PAGE_SIZE = 20;

/**
 * Links to other test cases (TM07): a PRECONDITION link names a case to run first, RELATES_TO is
 * informational. Shows both directions; emits the links so the detail view can show "run first"
 * above the preconditions text.
 */
@Component({
  selector: 'app-test-case-links',
  standalone: true,
  imports: [RouterLink, FormsModule, MatAutocompleteModule, MatButtonModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatSelectModule, MatTooltipModule, TranslateModule],
  template: `
    <ul class="links" data-test-id="test-case-links">
      @for (link of links(); track link.id) {
        <li [attr.data-test-id]="'test-case-link-' + link.testCaseKey">
          <span class="kind">{{ 'testCase.links.' + link.linkType + '_' + link.direction | translate }}</span>
          <a [routerLink]="['/projects', projectId, 'test-cases', link.testCaseId]">
            <strong>{{ link.testCaseKey }}</strong> {{ link.testCaseTitle }}
          </a>
          @if (canEdit) {
            <button mat-icon-button type="button" (click)="unlink(link)" [matTooltip]="'testCase.links.remove' | translate"
                    [attr.data-test-id]="'test-case-link-remove-' + link.testCaseKey">
              <mat-icon>link_off</mat-icon>
            </button>
          }
        </li>
      } @empty {
        <li class="none" data-test-id="test-case-links-empty">{{ 'testCase.links.none' | translate }}</li>
      }
    </ul>
    @if (canEdit) {
      @if (picking()) {
        <div class="picker">
          <mat-form-field appearance="outline" subscriptSizing="dynamic">
            <mat-label>{{ 'testCase.links.type' | translate }}</mat-label>
            <mat-select [(ngModel)]="linkType" data-test-id="test-case-link-type">
              <mat-option value="PRECONDITION">{{ 'testCase.links.PRECONDITION' | translate }}</mat-option>
              <mat-option value="RELATES_TO">{{ 'testCase.links.RELATES_TO' | translate }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="outline" subscriptSizing="dynamic" class="case-field">
            <mat-label>{{ 'testCase.links.choose' | translate }}</mat-label>
            <input matInput [matAutocomplete]="cases" (input)="search($any($event.target).value)"
                   data-test-id="test-case-link-search" />
            <mat-autocomplete #cases (optionSelected)="selection = $event.option.value" [displayWith]="display">
              @for (testCase of candidates(); track testCase.id) {
                <mat-option [value]="testCase">{{ testCase.key }} — {{ testCase.title }}</mat-option>
              }
            </mat-autocomplete>
          </mat-form-field>
          <button mat-flat-button type="button" [disabled]="!selection" (click)="link()"
                  data-test-id="test-case-link-confirm">{{ 'testCase.links.add' | translate }}</button>
          <button mat-button type="button" (click)="picking.set(false)">{{ 'common.cancel' | translate }}</button>
        </div>
      } @else {
        <button mat-stroked-button type="button" (click)="openPicker()" data-test-id="test-case-link-add-btn">
          <mat-icon>add_link</mat-icon> {{ 'testCase.links.add' | translate }}
        </button>
      }
    }
  `,
  styles: [`
    .links { list-style: none; margin: 0 0 8px; padding: 0; }
    .links li { display: flex; align-items: center; gap: 8px; font-size: 14px; min-height: 36px; }
    .links a { color: inherit; text-decoration: none; }
    .kind { color: var(--tm-text-secondary); font-size: 13px; min-width: 140px; }
    .none { color: var(--tm-text-secondary); font-size: 13px; }
    .picker { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
    .case-field { min-width: 280px; }
  `],
})
export class TestCaseLinksComponent implements OnInit {
  private readonly api = inject(TestCaseApiService);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) projectId!: string;
  @Input({ required: true }) testCaseId!: string;
  @Input() canEdit = false;
  @Output() readonly linksChange = new EventEmitter<TestCaseLink[]>();

  readonly links = signal<TestCaseLink[]>([]);
  readonly candidates = signal<TestCase[]>([]);
  readonly picking = signal(false);
  linkType: TestCaseLinkType = 'PRECONDITION';
  selection: TestCase | null = null;

  readonly display = (testCase: TestCase | null): string => (testCase ? `${testCase.key} — ${testCase.title}` : '');

  ngOnInit(): void {
    this.reload();
  }

  openPicker(): void {
    this.selection = null;
    this.candidates.set([]);
    this.picking.set(true);
  }

  /** Cases matching the text, minus this one; the server matches key and title. */
  search(term: string): void {
    this.selection = null;
    this.api.getAll(this.projectId, { q: term.trim() || undefined, size: PICKER_PAGE_SIZE })
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((page) => this.candidates.set(page.content.filter((tc) => tc.id !== this.testCaseId)));
  }

  link(): void {
    if (!this.selection) return;
    this.api.link(this.projectId, this.testCaseId, this.selection.id, this.linkType)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((links) => {
        this.picking.set(false);
        this.show(links);
      });
  }

  unlink(link: TestCaseLink): void {
    this.api.unlink(this.projectId, this.testCaseId, link.id)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.reload());
  }

  private reload(): void {
    this.api.getLinks(this.projectId, this.testCaseId)
      .pipe(take(1), takeUntilDestroyed(this.destroyRef))
      .subscribe((links) => this.show(links));
  }

  private show(links: TestCaseLink[]): void {
    this.links.set(links);
    this.linksChange.emit(links);
  }
}
