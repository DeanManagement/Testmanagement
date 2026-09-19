import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { IssueTrackerSettingsComponent } from './issue-tracker-settings.component';
import { IssueTrackerApiService } from '../../core/services/issue-tracker-api.service';
import { SaveIssueTrackerConfigRequest } from '../../shared/models/issue-tracker.model';

/** PRD-026: the Azure DevOps fields appear, and are sent, only for Azure DevOps. */
describe('IssueTrackerSettingsComponent – Azure DevOps', () => {
  let saveConfig: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    saveConfig = vi.fn(() => of({}));
    TestBed.configureTestingModule({
      imports: [IssueTrackerSettingsComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        {
          provide: IssueTrackerApiService,
          useValue: {
            getSupportedProviders: () => of(['GITLAB', 'AZURE_DEVOPS']),
            getConfig: () => of(null),
            saveConfig,
          },
        },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'p1' }) } } },
      ],
    });
  });

  function create() {
    const fixture = TestBed.createComponent(IssueTrackerSettingsComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.formBaseUrl = 'https://dev.azure.com/contoso';
    component.formProjectRef = 'Payments';
    component.formToken = 'pat';
    return { fixture, component };
  }

  function sent(): SaveIssueTrackerConfigRequest {
    return saveConfig.mock.calls[0][1] as SaveIssueTrackerConfigRequest;
  }

  it('sends the API version and work item type for Azure DevOps', () => {
    const { component } = create();
    component.formProvider = 'AZURE_DEVOPS';
    component.formApiVersion = ' 6.0 ';
    component.formWorkItemType = 'Issue';

    component.save();

    expect(sent()).toEqual(expect.objectContaining({ apiVersion: '6.0', workItemType: 'Issue' }));
  });

  it('sends neither for another tracker', () => {
    const { component } = create();
    component.formProvider = 'GITLAB';
    component.formApiVersion = '6.0';

    component.save();

    expect(sent().apiVersion).toBeUndefined();
    expect(sent().workItemType).toBeUndefined();
  });

  it('shows the fields only for Azure DevOps', () => {
    const { fixture, component } = create();
    const field = () => fixture.nativeElement.querySelector('[data-test-id="issue-tracker-work-item-type-input"]');

    component.formProvider = 'GITLAB';
    fixture.componentRef.changeDetectorRef.detectChanges();
    expect(field()).toBeNull();

    component.formProvider = 'AZURE_DEVOPS';
    fixture.componentRef.changeDetectorRef.detectChanges();
    expect(field()).not.toBeNull();
  });
});
