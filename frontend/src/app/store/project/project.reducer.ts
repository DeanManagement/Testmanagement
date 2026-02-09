import { createReducer, on } from '@ngrx/store';
import { ProjectActions } from './project.actions';
import { initialProjectState, projectAdapter } from './project.state';

export const projectReducer = createReducer(
  initialProjectState,

  on(ProjectActions.loadProjects, (state) => ({
    ...state,
    loading: true,
    error: null,
  })),

  on(ProjectActions.loadProjectsSuccess, (state, { projects }) =>
    projectAdapter.setAll(projects, { ...state, loading: false })
  ),

  on(ProjectActions.loadProjectsFailure, (state, { error }) => ({
    ...state,
    loading: false,
    error,
  })),

  on(ProjectActions.createProjectSuccess, (state, { project }) =>
    projectAdapter.addOne(project, state)
  ),

  on(ProjectActions.createProjectFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(ProjectActions.updateProjectSuccess, (state, { project }) =>
    projectAdapter.upsertOne(project, state)
  ),

  on(ProjectActions.updateProjectFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(ProjectActions.deleteProjectSuccess, (state, { id }) =>
    projectAdapter.removeOne(id, state)
  ),

  on(ProjectActions.deleteProjectFailure, (state, { error }) => ({
    ...state,
    error,
  })),

  on(ProjectActions.selectProject, (state, { id }) => ({
    ...state,
    selectedProjectId: id,
  }))
);
