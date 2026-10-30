import { Component, EventEmitter, Input, Output } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { CdkDrag, CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { ALL_BUG_STATUSES, BugReport, BugReportStatus, BugStatus, sortBugStatuses, statusOf } from '../../../shared/models/bug-report.model';

const MS_PER_DAY = 24 * 60 * 60 * 1000;

/** A card dropped on another lane: the list asks for a reason before anything changes. */
export interface BugStatusDrop {
  bug: BugReport;
  newStatus: BugReportStatus;
  /** TM47: the project status of the lane, when the project has its own. */
  newStatusId?: string;
  newStatusName?: string;
}

/**
 * One column: a project status (TM47), or a bare category for a project without statuses.
 * {@code canEnter} refuses a card the transition table does not let into it.
 */
export interface KanbanLane {
  id: string;
  category: BugReportStatus;
  status?: BugStatus;
  bugs: BugReport[];
  canEnter: (drag: CdkDrag<BugReport>) => boolean;
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
    this.bugs = reports;
    this.buildLanes();
  }
  /** TM47: the project's active statuses; none falls back to one lane per category. */
  @Input() set statuses(statuses: BugStatus[]) {
    this.projectStatuses = sortBugStatuses(statuses.filter((s) => !s.archived));
    this.buildLanes();
  }
  @Input() selectedIds: ReadonlySet<string> = new Set();
  /** TESTER and up: drag between lanes and select for bulk actions. */
  @Input() canWrite = false;
  /** Fixed for tests; otherwise the age is measured from now. */
  @Input() now: () => number = () => Date.now();

  @Output() readonly statusDrop = new EventEmitter<BugStatusDrop>();
  @Output() readonly selectionToggle = new EventEmitter<string>();

  lanes: KanbanLane[] = [];
  laneIds: string[] = [];
  private bugs: BugReport[] = [];
  private projectStatuses: BugStatus[] = [];

  private buildLanes(): void {
    if (this.projectStatuses.length === 0) {
      this.lanes = ALL_BUG_STATUSES.map((category) => ({
        id: category, category, bugs: this.bugs.filter((b) => b.status === category), canEnter: () => true,
      }));
    } else {
      this.lanes = this.projectStatuses.map((status) => ({
        id: status.id,
        category: status.category,
        status,
        bugs: this.bugs.filter((b) => statusOf(b, this.projectStatuses)?.id === status.id),
        canEnter: (drag: CdkDrag<BugReport>) => this.mayMove(drag.data, status),
      }));
    }
    this.laneIds = this.lanes.map((lane) => 'lane-' + lane.id);
  }

  /** The bug's own lane always takes it back; another only if the transition table allows. */
  private mayMove(bug: BugReport, target: BugStatus): boolean {
    const from = statusOf(bug, this.projectStatuses);
    return !from || from.id === target.id || from.allowedTargetIds.includes(target.id);
  }

  onDrop(event: CdkDragDrop<BugReport[]>, lane: KanbanLane): void {
    const bug: BugReport = event.item.data;
    if (!lane.status) {
      if (bug.status !== lane.category) {
        this.statusDrop.emit({ bug, newStatus: lane.category });
      }
      return;
    }
    if (statusOf(bug, this.projectStatuses)?.id !== lane.status.id) {
      this.statusDrop.emit({ bug, newStatus: lane.category, newStatusId: lane.status.id, newStatusName: lane.status.name });
    }
  }

  ageInDays(bug: BugReport): number {
    return Math.max(0, Math.floor((this.now() - new Date(bug.createdAt).getTime()) / MS_PER_DAY));
  }

  initials(name: string): string {
    return name.split(/\s+/).filter(Boolean).slice(0, 2).map((part) => part[0].toUpperCase()).join('');
  }
}
