import { Routes } from '@angular/router';
import { SharedStepListComponent } from './shared-step-list/shared-step-list.component';
import { SharedStepEditorComponent } from './shared-step-editor/shared-step-editor.component';
import { unsavedChangesGuard } from '../../core/guards/unsaved-changes.guard';

export const sharedStepsRoutes: Routes = [
  { path: '', component: SharedStepListComponent },
  { path: 'new', component: SharedStepEditorComponent, canDeactivate: [unsavedChangesGuard] },
  { path: ':sharedStepId', component: SharedStepEditorComponent, canDeactivate: [unsavedChangesGuard] },
];
