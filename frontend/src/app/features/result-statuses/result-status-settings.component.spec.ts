import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { CdkDragDrop } from '@angular/cdk/drag-drop';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ResultStatusSettingsComponent } from './result-status-settings.component';
import { ResultStatusApiService } from '../../core/services/result-status-api.service';
import { ProjectResultStatus } from '../../shared/models/result-status.model';

function status(id: string, position: number): ProjectResultStatus {
  return { id, name: id, color: '#000000', category: 'PASSED', position, archived: false };
}

/** TM24: the result-status settings page. */
describe('ResultStatusSettingsComponent', () => {
  let api: Record<string, ReturnType<typeof vi.fn>>;

  function create(): ResultStatusSettingsComponent {
    TestBed.configureTestingModule({
      imports: [ResultStatusSettingsComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: new Map([['id', 'p1']]) } } },
        { provide: ResultStatusApiService, useValue: api },
      ],
    });
    const component = TestBed.createComponent(ResultStatusSettingsComponent).componentInstance;
    component.ngOnInit();
    return component;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    api = {
      getAll: vi.fn(() => of([status('a', 0), status('b', 1), status('c', 2)])),
      update: vi.fn(() => of({})),
      create: vi.fn(() => of({})),
    };
  });

  it('saves the new position of only the statuses that moved', () => {
    const component = create();

    component.drop({ previousIndex: 0, currentIndex: 1 } as CdkDragDrop<ProjectResultStatus[]>);

    expect(api['update'].mock.calls).toEqual([['p1', 'b', { position: 0 }], ['p1', 'a', { position: 1 }]]);
  });

  it('adds a status with the chosen category and colour', () => {
    const component = create();
    component.newName = ' Not applicable ';
    component.newCategory = 'SKIPPED';
    component.newColor = '#123456';

    component.add();

    expect(api['create']).toHaveBeenCalledWith('p1', { name: 'Not applicable', color: '#123456', category: 'SKIPPED' });
  });

  it('does not save a rename to the same name', () => {
    const component = create();

    component.rename(status('a', 0), ' a ');

    expect(api['update']).not.toHaveBeenCalled();
  });
});
