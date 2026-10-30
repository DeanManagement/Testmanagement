import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BugStatusApiService } from '../../core/services/bug-status-api.service';
import { BugStatus } from '../../shared/models/bug-report.model';
import { BugStatusSettingsComponent } from './bug-status-settings.component';

const status = (id: string, name: string, category: BugStatus['category']) =>
  ({ id, name, color: '#000000', category, position: 0, archived: false, allowedTargetIds: [] }) as BugStatus;
const STATUSES = [status('new', 'New', 'NEW'), status('wip', 'In progress', 'IN_PROGRESS'), status('done', 'Closed', 'CLOSED')];

describe('BugStatusSettingsComponent', () => {
  let saveTransitions: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    saveTransitions = vi.fn(() => of([]));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [BugStatusSettingsComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'p' }) } } },
        {
          provide: BugStatusApiService,
          useValue: { getAll: () => of(STATUSES), getTransitions: () => of([]), saveTransitions },
        },
      ],
    });
  });

  function render(): BugStatusSettingsComponent {
    const fixture = TestBed.createComponent(BugStatusSettingsComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  it('should save the ticked moves as the transition table', () => {
    const page = render();
    page.toggle(STATUSES[0], STATUSES[1]);

    page.saveTransitions();

    expect(saveTransitions).toHaveBeenCalledWith('p', [{ fromStatusId: 'new', toStatusId: 'wip' }]);
  });

  it('should warn about open statuses a bug could not leave once moves are restricted', () => {
    const page = render();

    page.toggle(STATUSES[0], STATUSES[1]);

    expect(page.deadEnds.map((s) => s.name)).toEqual(['In progress']);
  });

  it('should not warn while any move is allowed', () => {
    expect(render().deadEnds).toEqual([]);
  });
});
