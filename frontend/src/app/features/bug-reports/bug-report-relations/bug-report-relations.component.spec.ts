import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BugReportApiService } from '../../../core/services/bug-report-api.service';
import { BugReportRelations } from '../../../shared/models/bug-report.model';
import { BugReportRelationsComponent } from './bug-report-relations.component';

const NONE: BugReportRelations = { testCases: [], requirements: [], relations: [] };

describe('BugReportRelationsComponent', () => {
  let api: { getRelations: ReturnType<typeof vi.fn>; relate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    api = { getRelations: vi.fn(() => of(NONE)), relate: vi.fn(() => of(NONE)) };
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [BugReportRelationsComponent, TranslateModule.forRoot()],
      providers: [provideRouter([]), provideNoopAnimations(), { provide: BugReportApiService, useValue: api }],
    });
  });

  function render() {
    const fixture = TestBed.createComponent(BugReportRelationsComponent);
    fixture.componentRef.setInput('projectId', 'p');
    fixture.componentRef.setInput('bugId', 'bug-1');
    fixture.componentRef.setInput('bugKey', 'SN-BUG-1');
    fixture.componentRef.setInput('canEdit', true);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('should save "is blocked by" as the other bug blocking this one', () => {
    const component = render();
    component.relationChoice = 'BLOCKED_BY';
    component.bugRef = 'SN-BUG-7';

    component.addRelation();

    expect(api.relate).toHaveBeenCalledWith('p', 'SN-BUG-7', 'SN-BUG-1', 'BLOCKS');
  });

  it('should save "blocks" from this bug', () => {
    const component = render();
    component.relationChoice = 'BLOCKS';
    component.bugRef = 'SN-BUG-7';

    component.addRelation();

    expect(api.relate).toHaveBeenCalledWith('p', 'bug-1', 'SN-BUG-7', 'BLOCKS');
  });

  it('should label an incoming BLOCKS relation as blocked by', () => {
    const component = render();

    const label = component.relationLabel({ id: 'r', type: 'BLOCKS', outgoing: false, bugId: 'b', bugKey: 'K',
      bugTitle: 'T', bugStatus: 'OPEN' });

    expect(label).toBe('bugReport.relations.BLOCKED_BY');
  });
});
