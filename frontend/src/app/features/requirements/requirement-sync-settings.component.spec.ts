import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { RequirementSyncSettingsComponent } from './requirement-sync-settings.component';
import { RequirementApiService } from '../../core/services/requirement-api.service';
import { RequirementSyncConfig } from '../../shared/models/requirement.model';

const SAVED: RequirementSyncConfig = {
  enabled: true, scheduled: false, query: 'SELECT [System.Id] FROM WorkItems', externalIdPrefix: 'AB#',
  approvedStates: 'Active', obsoleteStates: 'Removed', lastSyncedAt: null, lastError: null, lastErrorAt: null,
};

/** TM19: the sync settings card loads, saves and previews the Azure DevOps requirement sync. */
describe('RequirementSyncSettingsComponent', () => {
  let saveSyncConfig: ReturnType<typeof vi.fn>;
  let runSync: ReturnType<typeof vi.fn>;

  function create(config: RequirementSyncConfig | null) {
    saveSyncConfig = vi.fn(() => of(SAVED));
    runSync = vi.fn(() => of({ created: 2, updated: 1, obsoleted: 0, unchanged: 5, dryRun: true }));
    TestBed.configureTestingModule({
      imports: [RequirementSyncSettingsComponent, TranslateModule.forRoot()],
      providers: [
        provideNoopAnimations(),
        { provide: RequirementApiService, useValue: { getSyncConfig: () => of(config), saveSyncConfig, runSync } },
      ],
    });
    const fixture = TestBed.createComponent(RequirementSyncSettingsComponent);
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance };
  }

  beforeEach(() => TestBed.resetTestingModule());

  it('fills the form from the saved settings', () => {
    const { component } = create(SAVED);

    expect(component.query).toBe(SAVED.query);
    expect(component.externalIdPrefix).toBe('AB#');
    expect(component.enabled).toBe(true);
  });

  it('sends a blank prefix as null and the query trimmed', () => {
    const { component } = create(null);
    component.query = '  SELECT [System.Id] FROM WorkItems ';
    component.externalIdPrefix = ' ';

    component.save();

    expect(saveSyncConfig).toHaveBeenCalledWith('p1', expect.objectContaining({
      query: 'SELECT [System.Id] FROM WorkItems', externalIdPrefix: null,
    }));
  });

  it('previews with a dry run', () => {
    const { component } = create(SAVED);

    component.dryRun();

    expect(runSync).toHaveBeenCalledWith('p1', true);
    expect(component.preview?.created).toBe(2);
  });

  it('offers the preview only once settings are saved', () => {
    const { fixture } = create(null);

    expect(fixture.nativeElement.querySelector('[data-test-id="requirement-sync-dry-run"]')).toBeNull();
  });
});
