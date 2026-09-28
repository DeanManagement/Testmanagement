import { TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { EnvironmentApiService } from '../../../core/services/environment-api.service';
import { ProjectWorkflow } from '../../../shared/models/build-server.model';
import { TriggerPipelineDialogComponent, TriggerPipelineDialogData } from './trigger-pipeline-dialog.component';

describe('TriggerPipelineDialogComponent', () => {
  function create(): { dialog: TriggerPipelineDialogComponent; close: ReturnType<typeof vi.fn> } {
    const close = vi.fn();
    const workflow: ProjectWorkflow = {
      id: 'w1', name: 'E2E', serverName: 'Azure', provider: 'AZURE_DEVOPS', defaultRef: 'main', defaultParameters: { SUITE: 'smoke' },
    };
    TestBed.configureTestingModule({
      imports: [TriggerPipelineDialogComponent, TranslateModule.forRoot()],
      providers: [
        provideNoopAnimations(),
        { provide: MatDialogRef, useValue: { close } },
        { provide: MAT_DIALOG_DATA, useValue: { projectId: 'p1', workflow } satisfies TriggerPipelineDialogData },
        { provide: EnvironmentApiService, useValue: { getActive: () => of([{ id: 'env-1', name: 'staging' }]) } },
      ],
    });
    const fixture = TestBed.createComponent(TriggerPipelineDialogComponent);
    fixture.detectChanges();
    return { dialog: fixture.componentInstance, close };
  }

  it('triggers without an environment unless one is picked', () => {
    const { dialog, close } = create();

    dialog.confirm();

    expect(close).toHaveBeenCalledWith({ ref: 'main', parameters: { SUITE: 'smoke' }, environmentId: null });
  });

  it('passes the picked environment along with the trigger', () => {
    const { dialog, close } = create();
    dialog.environmentId = 'env-1';

    dialog.confirm();

    expect(close).toHaveBeenCalledWith(expect.objectContaining({ environmentId: 'env-1' }));
  });
});
