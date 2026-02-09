import { createActionGroup, emptyProps, props } from '@ngrx/store';
import { CreateProjectRequest, Project, UpdateProjectRequest } from '../../shared/models/project.model';

export const ProjectActions = createActionGroup({
  source: 'Projects',
  events: {
    'Load Projects': emptyProps(),
    'Load Projects Success': props<{ projects: Project[] }>(),
    'Load Projects Failure': props<{ error: string }>(),
    'Create Project': props<{ request: CreateProjectRequest }>(),
    'Create Project Success': props<{ project: Project }>(),
    'Create Project Failure': props<{ error: string }>(),
    'Update Project': props<{ id: string; request: UpdateProjectRequest }>(),
    'Update Project Success': props<{ project: Project }>(),
    'Update Project Failure': props<{ error: string }>(),
    'Delete Project': props<{ id: string }>(),
    'Delete Project Success': props<{ id: string }>(),
    'Delete Project Failure': props<{ error: string }>(),
    'Select Project': props<{ id: string }>(),
  },
});
