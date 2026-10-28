import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SavedFiltersMenuComponent } from './saved-filters-menu.component';
import { SavedFilterApiService } from '../../../core/services/saved-filter-api.service';
import { SavedFilter } from '../../models/saved-filter.model';

const MINE: SavedFilter = { id: 'f1', name: 'Open highs', view: 'TEST_CASES', queryString: 'priority=HIGH',
  shared: false, mine: true, editable: true };
const THEIRS: SavedFilter = { id: 'f2', name: 'Team', view: 'TEST_CASES', queryString: 'q=pay',
  shared: true, mine: false, editable: false };

describe('SavedFiltersMenuComponent', () => {
  const api = { getVisible: vi.fn(), create: vi.fn() };

  function create(): SavedFiltersMenuComponent {
    TestBed.configureTestingModule({
      imports: [SavedFiltersMenuComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: SavedFilterApiService, useValue: api },
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of({ name: 'Mine', shared: true }) }) } },
      ],
    });
    const fixture = TestBed.createComponent(SavedFiltersMenuComponent);
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.componentRef.setInput('view', 'TEST_CASES');
    return fixture.componentInstance;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    api.getVisible.mockReset().mockReturnValue(of([MINE, THEIRS]));
    api.create.mockReset().mockReturnValue(of(MINE));
  });

  it('splits own filters from the ones colleagues shared', () => {
    const menu = create();

    menu.load();

    expect(menu.sections().map((s) => s.filters.map((f) => f.name))).toEqual([['Open highs'], ['Team']]);
  });

  it('saves the current query string without the page', () => {
    const menu = create();
    vi.spyOn(TestBed.inject(Router), 'url', 'get').mockReturnValue('/projects/p1/test-cases?status=READY&page=2');

    menu.saveCurrent();

    expect(api.create).toHaveBeenCalledWith('p1',
      { name: 'Mine', shared: true, view: 'TEST_CASES', queryString: 'status=READY' });
  });

  it('applies a filter by navigating to its query string', () => {
    const menu = create();
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'url', 'get').mockReturnValue('/projects/p1/test-cases?q=old');
    const navigate = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);

    menu.apply(MINE);

    expect(navigate).toHaveBeenCalledWith('/projects/p1/test-cases?priority=HIGH');
  });
});
