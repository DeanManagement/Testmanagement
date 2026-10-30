import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { CdkDrag, CdkDragDrop } from '@angular/cdk/drag-drop';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BugReportKanbanComponent } from './bug-report-kanban.component';
import { BugReport, BugStatus } from '../../../shared/models/bug-report.model';

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

    expect(board.lanes.map((l) => l.category)).toEqual(['NEW', 'OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']);
    expect(board.lanes[0].bugs.map((b) => b.id)).toEqual(['1', '3']);
  });

  it('reports a drop on another lane without moving the card itself', () => {
    board.reports = [];
    const emitted = vi.fn();
    board.statusDrop.subscribe(emitted);
    const dragged = bug('1', 'NEW');

    board.onDrop({ item: { data: dragged } } as CdkDragDrop<BugReport[]>, board.lanes[1]);
    board.onDrop({ item: { data: dragged } } as CdkDragDrop<BugReport[]>, board.lanes[0]);

    expect(emitted).toHaveBeenCalledTimes(1);
    expect(emitted).toHaveBeenCalledWith({ bug: dragged, newStatus: 'OPEN' });
  });

  describe('with project statuses (TM47)', () => {
    const status = (id: string, name: string, category: BugReport['status'], allowedTargetIds: string[], position = 0) =>
      ({ id, name, color: '#000000', category, position, archived: false, allowedTargetIds }) as BugStatus;
    const statuses = [
      status('closed', 'Closed', 'CLOSED', ['new']),
      status('new', 'New', 'NEW', ['retest']),
      status('retest', 'Ready for retest', 'RESOLVED', ['closed']),
    ];

    beforeEach(() => {
      board.statuses = statuses;
      board.reports = [{ ...bug('1', 'NEW'), customStatusId: 'new' } as BugReport, bug('2', 'RESOLVED')];
    });

    it('has one lane per status, ordered by category', () => {
      expect(board.lanes.map((l) => l.status?.name)).toEqual(['New', 'Ready for retest', 'Closed']);
    });

    it('puts a bug without a status of its own in the first status of its category', () => {
      expect(board.lanes[1].bugs.map((b) => b.id)).toEqual(['2']);
    });

    it('lets a card only into lanes the transitions allow', () => {
      const card = { data: board.lanes[0].bugs[0] } as CdkDrag<BugReport>;

      expect(board.lanes.map((lane) => lane.canEnter(card))).toEqual([true, true, false]);
    });

    it('reports the project status of the lane dropped on', () => {
      const emitted = vi.fn();
      board.statusDrop.subscribe(emitted);
      const dragged = board.lanes[0].bugs[0];

      board.onDrop({ item: { data: dragged } } as CdkDragDrop<BugReport[]>, board.lanes[1]);

      expect(emitted).toHaveBeenCalledWith({ bug: dragged, newStatus: 'RESOLVED', newStatusId: 'retest',
        newStatusName: 'Ready for retest' });
    });
  });

  it('shows the age in whole days', () => {
    board.now = () => new Date('2026-09-13T07:00:00Z').getTime();

    expect(board.ageInDays(bug('1', 'NEW', '2026-09-10T08:00:00Z'))).toBe(2);
  });

  it('abbreviates the assignee to initials', () => {
    expect(board.initials('Ada Lovelace King')).toBe('AL');
  });
});
