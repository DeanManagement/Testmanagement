import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BuildServerSettingsComponent } from './build-server-settings.component';
import { BuildServerApiService } from '../../core/services/build-server-api.service';
import { ProjectApiService } from '../../core/services/project-api.service';
import { BuildServerConfig, SaveBuildServerConfigRequest, SaveBuildWorkflowRequest } from '../../shared/models/build-server.model';
import { Project } from '../../shared/models/project.model';

/** PRD-026: pulling test results is offered, and sent, only for Azure DevOps workflows. */
describe('BuildServerSettingsComponent – Azure DevOps', () => {
  let createWorkflow: ReturnType<typeof vi.fn>;

  const server = (id: string, provider: BuildServerConfig['provider']): BuildServerConfig => ({
    id, name: id, provider, baseUrl: 'https://x', active: true, tokenSet: true,
    lastError: null, lastErrorAt: null, updatedAt: '', apiVersion: null, projectIds: null,
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

/** A server open to every project, or limited to some: what is sent, and whom workflows can reach. */
describe('BuildServerSettingsComponent – project scope', () => {
  let createServer: ReturnType<typeof vi.fn>;
  const projects = [{ id: 'p1', name: 'Payments' }, { id: 'p2', name: 'Billing' }] as Project[];
  const limited: BuildServerConfig = {
    id: 's1', name: 'Team A', provider: 'AZURE_DEVOPS', baseUrl: 'https://x', active: true, tokenSet: true,
    lastError: null, lastErrorAt: null, updatedAt: '', apiVersion: null, projectIds: ['p2'],
  };

  function create(): BuildServerSettingsComponent {
    createServer = vi.fn(() => of(limited));
    TestBed.configureTestingModule({
      imports: [BuildServerSettingsComponent, TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideNoopAnimations(),
        {
          provide: BuildServerApiService,
          useValue: {
            getSupportedProviders: () => of(['AZURE_DEVOPS']),
            getServers: () => of([limited]),
            createServer,
          },
        },
        { provide: ProjectApiService, useValue: { getAll: () => of(projects) } },
      ],
    });
    const fixture = TestBed.createComponent(BuildServerSettingsComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  function sent(): SaveBuildServerConfigRequest {
    return createServer.mock.calls[0][0] as SaveBuildServerConfigRequest;
  }

  it('opens a new server to every project unless limited', () => {
    const component = create();
    component.openServerForm(null);

    component.saveServer();

    expect(sent().projectIds).toBeNull();
  });

  it('sends the chosen projects for a limited server', () => {
    const component = create();
    component.openServerForm(null);
    component.sAllProjects = false;
    component.sProjectIds = ['p1'];

    component.saveServer();

    expect(sent().projectIds).toEqual(['p1']);
  });

  it('offers only projects in the server scope for workflow assignment', () => {
    const component = create();

    expect(component.projectsFor(limited).map((p) => p.id)).toEqual(['p2']);
  });
});
