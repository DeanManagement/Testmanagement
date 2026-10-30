import { Component, DestroyRef, inject, input, OnChanges, signal, SimpleChanges } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, switchMap } from 'rxjs';
import { BugReportApiService } from '../../../core/services/bug-report-api.service';
import { BugRelation, BugReportRelations } from '../../../shared/models/bug-report.model';

/** How the form offers a bug relation; BLOCKED_BY is saved as the other bug's BLOCKS. */
type RelationChoice = 'BLOCKS' | 'BLOCKED_BY' | 'RELATES_TO';
type ItemKind = 'test-cases' | 'requirements';

/** A bug's hand-made links to test cases, requirements and other bugs (TM46). Added by key. */
@Component({
  selector: 'app-bug-report-relations',
  standalone: true,
  imports: [FormsModule, RouterLink, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule,
    MatSelectModule, TranslateModule],
  templateUrl: './bug-report-relations.component.html',
  styleUrl: './bug-report-relations.component.scss',
})
export class BugReportRelationsComponent implements OnChanges {
  private readonly api = inject(BugReportApiService);
  private readonly destroyRef = inject(DestroyRef);

  readonly projectId = input.required<string>();
  readonly bugId = input.required<string>();
  readonly bugKey = input.required<string>();
  readonly canEdit = input(false);

  readonly relations = signal<BugReportRelations>({ testCases: [], requirements: [], relations: [] });
  readonly error = signal<string | null>(null);

  testCaseRef = '';
  requirementRef = '';
  bugRef = '';
  relationChoice: RelationChoice = 'RELATES_TO';
  readonly relationChoices: RelationChoice[] = ['BLOCKS', 'BLOCKED_BY', 'RELATES_TO'];

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['projectId'] || changes['bugId']) {
      this.run(this.api.getRelations(this.projectId(), this.bugId()));
    }
  }

  /** The label for a relation as seen from this bug: blocks, blocked by, or related to. */
  relationLabel(relation: BugRelation): string {
    if (relation.type === 'RELATES_TO') {
      return 'bugReport.relations.RELATES_TO';
    }
    return relation.outgoing ? 'bugReport.relations.BLOCKS' : 'bugReport.relations.BLOCKED_BY';
  }

  addItem(kind: ItemKind): void {
    const reference = (kind === 'test-cases' ? this.testCaseRef : this.requirementRef).trim();
    if (!reference) {
      return;
    }
    this.run(this.api.linkItem(this.projectId(), this.bugId(), kind, reference), () => {
      this.testCaseRef = '';
      this.requirementRef = '';
    });
  }

  removeItem(kind: ItemKind, id: string): void {
    this.run(this.api.unlinkItem(this.projectId(), this.bugId(), kind, id));
  }

  addRelation(): void {
    const other = this.bugRef.trim();
    if (!other) {
      return;
    }
    const saved$ = this.relationChoice === 'BLOCKED_BY'
      ? this.api.relate(this.projectId(), other, this.bugKey(), 'BLOCKS')
        .pipe(switchMap(() => this.api.getRelations(this.projectId(), this.bugId())))
      : this.api.relate(this.projectId(), this.bugId(), other, this.relationChoice);
    this.run(saved$, () => (this.bugRef = ''));
  }

  removeRelation(relation: BugRelation): void {
    this.run(this.api.unrelate(this.projectId(), this.bugId(), relation.id));
  }

  private run(request$: Observable<BugReportRelations>, onSuccess?: () => void): void {
    request$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (relations) => {
        this.relations.set(relations);
        this.error.set(null);
        onSuccess?.();
      },
      error: (err) => this.error.set(err?.error?.message ?? 'bugReport.relations.failed'),
    });
  }
}
