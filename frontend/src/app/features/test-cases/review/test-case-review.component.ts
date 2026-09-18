import { ChangeDetectorRef, Component, DestroyRef, OnChanges, inject, input, output } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';
import { forkJoin } from 'rxjs';
import { take } from 'rxjs/operators';
import { ProjectMemberApiService } from '../../../core/services/project-member-api.service';
import { TestCaseApiService } from '../../../core/services/test-case-api.service';
import { ReviewCapabilities, TestCase } from '../../../shared/models/test-case.model';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';

/**
 * Review actions and approval record for one test case (PRD-033). What the user may do comes
 * from the server's capabilities, so the role and not-the-author rules live in one place.
 */
@Component({
  selector: 'app-test-case-review',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, TranslateModule,
    LocalizedDatePipe],
  template: `
    @if (capabilities; as caps) {
      <div class="review" data-test-id="test-case-review">
        @if (testCase().approvedVersion !== null) {
          <p class="approval" data-test-id="test-case-approval">
            <mat-icon>verified</mat-icon>
            {{ 'review.approvedLine' | translate: {
              version: testCase().approvedVersion,
              name: approverName,
              date: (testCase().approvedAt | localizedDate: 'mediumDate')
            } }}
            @if (testCase().approvedVersion !== testCase().currentVersion) {
              <button mat-button type="button" (click)="compareApproved.emit(testCase().approvedVersion!)"
                      data-test-id="test-case-compare-approved">
                {{ 'review.compareWithApproved' | translate }}
              </button>
            }
          </p>
        } @else if (caps.reviewRequired && testCase().status === 'ACTIVE') {
          <p class="approval legacy">{{ 'review.legacyApproved' | translate }}</p>
        }

        @if (caps.canSubmit) {
          <button mat-stroked-button (click)="submit()" [disabled]="busy" data-test-id="test-case-submit-review">
            <mat-icon>rate_review</mat-icon>
            {{ 'review.submit' | translate }}
          </button>
        }

        @if (testCase().status === 'IN_REVIEW') {
          @if (caps.canApprove) {
            <div class="decision">
              <mat-form-field appearance="outline" class="comment-field" subscriptSizing="dynamic">
                <mat-label>{{ 'review.comment' | translate }}</mat-label>
                <input matInput [(ngModel)]="comment" maxlength="2000" data-test-id="test-case-review-comment" />
              </mat-form-field>
              <button mat-flat-button (click)="approve()" [disabled]="busy" data-test-id="test-case-approve">
                <mat-icon>check</mat-icon>
                {{ 'review.approve' | translate: { version: testCase().currentVersion } }}
              </button>
              <button mat-stroked-button (click)="requestChanges()" [disabled]="busy"
                      data-test-id="test-case-request-changes">
                {{ 'review.requestChanges' | translate }}
              </button>
            </div>
          } @else if (caps.reason) {
            <p class="hint" data-test-id="test-case-review-reason">{{ caps.reason }}</p>
          }
        }
      </div>
    }
  `,
  styles: [`
    .review { display: flex; flex-direction: column; align-items: flex-start; gap: 0.75rem; }
    .approval { display: flex; align-items: center; flex-wrap: wrap; gap: 0.5rem; margin: 0; }
    .approval mat-icon { color: var(--tm-success); }
    .legacy, .hint { color: var(--tm-text-secondary); margin: 0; }
    .decision { display: flex; align-items: center; flex-wrap: wrap; gap: 0.5rem; width: 100%; }
    .comment-field { flex: 1 1 16rem; }
  `],
})
export class TestCaseReviewComponent implements OnChanges {
  private readonly api = inject(TestCaseApiService);
  private readonly memberApi = inject(ProjectMemberApiService);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  readonly projectId = input.required<string>();
  readonly testCase = input.required<TestCase>();
  /** The case changed on the server; the parent reloads it. */
  readonly changed = output<TestCase>();
  /** Show the diff from this (approved) version to the current one. */
  readonly compareApproved = output<number>();

  capabilities: ReviewCapabilities | null = null;
  approverName = '';
  comment = '';
  busy = false;

  ngOnChanges(): void {
    forkJoin({
      capabilities: this.api.reviewCapabilities(this.projectId(), this.testCase().id),
      members: this.memberApi.getByProject(this.projectId()),
    }).pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(({ capabilities, members }) => {
      this.capabilities = capabilities;
      const approver = members.find((member) => member.userId === this.testCase().approvedBy);
      this.approverName = approver?.displayName ?? '—';
      this.cdr.detectChanges();
    });
  }

  submit(): void {
    this.run(this.api.submitForReview(this.projectId(), this.testCase().id));
  }

  approve(): void {
    this.run(this.api.approve(this.projectId(), this.testCase().id, this.testCase().currentVersion));
  }

  requestChanges(): void {
    this.run(this.api.requestChanges(this.projectId(), this.testCase().id, this.comment.trim() || undefined));
  }

  private run(action: ReturnType<TestCaseApiService['approve']>): void {
    this.busy = true;
    action.pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (updated) => {
        this.busy = false;
        this.comment = '';
        this.changed.emit(updated);
      },
      // The error interceptor shows the server's message (e.g. a 409 when the case moved on).
      error: () => {
        this.busy = false;
        this.cdr.detectChanges();
      },
    });
  }
}
