import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { BuildServerApiService } from '../../../core/services/build-server-api.service';
import { ProjectWorkflow } from '../../../shared/models/build-server.model';
import { ProjectWorkflowSettingsComponent } from './project-workflow-settings.component';

describe('ProjectWorkflowSettingsComponent', () => {
  const workflow = (id: string): ProjectWorkflow =>
    ({ id, name: id, serverName: 'Azure', provider: 'AZURE_DEVOPS', defaultRef: null, defaultParameters: {} });

  function create(available: ProjectWorkflow[], offered: ProjectWorkflow[]) {
    const setProjectWorkflows = vi.fn(() => of(undefined));
    TestBed.configureTestingModule({
      imports: [ProjectWorkflowSettingsComponent, TranslateModule.forRoot()],
      providers: [
        provideNoopAnimations(),
        {
          provide: BuildServerApiService,
          useValue: {
            getAvailableWorkflows: () => of(available),
            getProjectWorkflows: () => of(offered),
            setProjectWorkflows,
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(ProjectWorkflowSettingsComponent);
    fixture.componentRef.setInput('projectId', 'p1');
    fixture.detectChanges();
    return { fixture, component: fixture.componentInstance, setProjectWorkflows };
  }

  it('starts from the workflows the project already offers', () => {
    const { component } = create([workflow('a'), workflow('b')], [workflow('b')]);

    expect(component.selectedIds).toEqual(['b']);
  });

  it('saves exactly the chosen workflows', () => {
    const { component, setProjectWorkflows } = create([workflow('a'), workflow('b')], []);
    component.selectedIds = ['a'];

    component.save();

    expect(setProjectWorkflows).toHaveBeenCalledWith('p1', ['a']);
  });

  it('explains that nothing is available when no build server is open to the project', () => {
    const { fixture } = create([], []);

    expect((fixture.nativeElement as HTMLElement).querySelector('[data-test-id="project-workflows-none"]')).not.toBeNull();
  });
});
