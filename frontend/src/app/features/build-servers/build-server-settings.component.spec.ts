import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BuildServerSettingsComponent } from './build-server-settings.component';
import { BuildServerApiService } from '../../core/services/build-server-api.service';
import { ProjectApiService } from '../../core/services/project-api.service';
import { BuildServerConfig, SaveBuildWorkflowRequest } from '../../shared/models/build-server.model';

/** PRD-026: pulling test results is offered, and sent, only for Azure DevOps workflows. */
describe('BuildServerSettingsComponent – Azure DevOps', () => {
  let createWorkflow: ReturnType<typeof vi.fn>;

  const server = (id: string, provider: BuildServerConfig['provider']): BuildServerConfig => ({
    id, name: id, provider, baseUrl: 'https://x', active: true, tokenSet: true,
    lastError: null, lastErrorAt: null, updatedAt: '', apiVersion: null,
  });

  beforeEach(() => {
    createWorkflow = vi.fn(() => of({ id: 'w1', buildServerConfigId: 'azure', projectIds: [] }));
    TestBed.configureTestingModule({
      imports: [BuildServerSettingsComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        {
          provide: BuildServerApiService,
          useValue: {
            getSupportedProviders: () => of(['GITLAB_CI', 'AZURE_DEVOPS']),
            getServers: () => of([server('azure', 'AZURE_DEVOPS'), server('gitlab', 'GITLAB_CI')]),
            getWorkflows: () => of([]),
            createWorkflow,
          },
        },
        { provide: ProjectApiService, useValue: { getAll: () => of([]) } },
      ],
    });
  });

  function openWorkflowForm(serverId: string) {
    const fixture = TestBed.createComponent(BuildServerSettingsComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    component.openWorkflowForm(serverId, null);
    component.wName = 'CI';
    component.wRepoRef = 'Payments';
    component.wWorkflowRef = '42';
    component.wPullTestResults = true;
    return component;
  }

  function sent(): SaveBuildWorkflowRequest {
    return createWorkflow.mock.calls[0][1] as SaveBuildWorkflowRequest;
  }

  it('sends the pull setting for an Azure DevOps workflow', () => {
    const component = openWorkflowForm('azure');
    expect(component.workflowServerIsAzure).toBe(true);

    component.saveWorkflow();

    expect(sent().pullTestResults).toBe(true);
  });

  it('never asks another provider to pull', () => {
    const component = openWorkflowForm('gitlab');
    expect(component.workflowServerIsAzure).toBe(false);

    component.saveWorkflow();

    expect(sent().pullTestResults).toBe(false);
  });
});
