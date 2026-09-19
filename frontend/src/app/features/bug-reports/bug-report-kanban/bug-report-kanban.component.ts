import { Component, EventEmitter, Input, Output } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { ALL_BUG_STATUSES, BugReport, BugReportStatus } from '../../../shared/models/bug-report.model';

const MS_PER_DAY = 24 * 60 * 60 * 1000;

/** A card dropped on another lane: the list asks for a reason before anything changes. */
export interface BugStatusDrop {
  bug: BugReport;
  newStatus: BugReportStatus;
}

/**
 * The bugs of the current filter as lanes, one per status (PRD-045 §3.4). Presentational: a drop is
 * reported, never applied, so cancelling the status dialog leaves the card where it was.
 */
@Component({
  selector: 'app-bug-report-kanban',
  standalone: true,
  imports: [LowerCasePipe, RouterLink, DragDropModule, MatCheckboxModule, MatTooltipModule, TranslateModule],
  templateUrl: './bug-report-kanban.component.html',
  styleUrl: './bug-report-kanban.component.scss',
})
export class BugReportKanbanComponent {
  @Input({ required: true }) projectId!: string;
  @Input({ required: true }) set reports(reports: BugReport[]) {
    this.lanes = ALL_BUG_STATUSES.map((status) => ({ status, bugs: reports.filter((b) => b.status === status) }));
  }
  @Input() selectedIds: ReadonlySet<string> = new Set();
  /** TESTER and up: drag between lanes and select for bulk actions. */
  @Input() canWrite = false;
  /** Fixed for tests; otherwise the age is measured from now. */
  @Input() now: () => number = () => Date.now();

  @Output() readonly statusDrop = new EventEmitter<BugStatusDrop>();
  @Output() readonly selectionToggle = new EventEmitter<string>();

  lanes: { status: BugReportStatus; bugs: BugReport[] }[] = [];
  readonly laneIds = ALL_BUG_STATUSES.map((s) => 'lane-' + s);

  onDrop(event: CdkDragDrop<BugReport[]>, newStatus: BugReportStatus): void {
    const bug: BugReport = event.item.data;
    if (bug.status !== newStatus) {
      this.statusDrop.emit({ bug, newStatus });
    }
  }

  ageInDays(bug: BugReport): number {
    return Math.max(0, Math.floor((this.now() - new Date(bug.createdAt).getTime()) / MS_PER_DAY));
  }

  initials(name: string): string {
    return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0].toUpperCase()).join('');
  }
}
