import { createFeatureSelector, createSelector } from '@ngrx/store';
import { ProjectState, projectAdapter } from './project.state';

export const selectProjectState = createFeatureSelector<ProjectState>('projects');

const { selectAll, selectEntities } = projectAdapter.getSelectors();

export const selectAllProjects = createSelector(selectProjectState, selectAll);

export const selectProjectEntities = createSelector(selectProjectState, selectEntities);

export const selectProjectsLoading = createSelector(
  selectProjectState,
  (state) => state.loading
);

export const selectProjectsError = createSelector(
  selectProjectState,
  (state) => state.error
);

export const selectSelectedProjectId = createSelector(
  selectProjectState,
  (state) => state.selectedProjectId
);

export const selectProjectById = (id: string) =>
  createSelector(selectProjectEntities, (entities) => entities[id]);
