import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BugReportKanbanComponent } from './bug-report-kanban.component';
import { BugReport } from '../../../shared/models/bug-report.model';

describe('BugReportKanbanComponent', () => {
  let board: BugReportKanbanComponent;

  const bug = (id: string, status: BugReport['status'], createdAt = '2026-09-10T08:00:00Z') =>
    ({ id, key: `P-BUG-${id}`, title: id, status, priority: 'LOW', createdAt, assigneeName: null }) as unknown as BugReport;

  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [BugReportKanbanComponent, TranslateModule.forRoot()],
      providers: [provideRouter([])],
    });
    board = TestBed.createComponent(BugReportKanbanComponent).componentInstance;
    board.projectId = 'p1';
  });

  it('puts every bug in the lane of its status, one lane per status', () => {
    board.reports = [bug('1', 'NEW'), bug('2', 'CLOSED'), bug('3', 'NEW')];

    expect(board.lanes.map((l) => l.status)).toEqual(['NEW', 'OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']);
    expect(board.lanes[0].bugs.map((b) => b.id)).toEqual(['1', '3']);
  });

  it('reports a drop on another lane without moving the card itself', () => {
    const emitted = vi.fn();
    board.statusDrop.subscribe(emitted);
    const dragged = bug('1', 'NEW');

    board.onDrop({ item: { data: dragged } } as CdkDragDrop<BugReport[]>, 'OPEN');
    board.onDrop({ item: { data: dragged } } as CdkDragDrop<BugReport[]>, 'NEW');

    expect(emitted).toHaveBeenCalledTimes(1);
    expect(emitted).toHaveBeenCalledWith({ bug: dragged, newStatus: 'OPEN' });
  });

  it('shows the age in whole days', () => {
    board.now = () => new Date('2026-09-13T07:00:00Z').getTime();

    expect(board.ageInDays(bug('1', 'NEW', '2026-09-10T08:00:00Z'))).toBe(2);
  });

  it('abbreviates the assignee to initials', () => {
    expect(board.initials('Ada Lovelace King')).toBe('AL');
  });
});
