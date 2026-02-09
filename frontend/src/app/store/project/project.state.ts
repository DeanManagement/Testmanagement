import { EntityState, createEntityAdapter } from '@ngrx/entity';
import { Project } from '../../shared/models/project.model';

export const projectAdapter = createEntityAdapter<Project>();

export interface ProjectState extends EntityState<Project> {
  loading: boolean;
  error: string | null;
  selectedProjectId: string | null;
}

export const initialProjectState: ProjectState = projectAdapter.getInitialState({
  loading: false,
  error: null,
  selectedProjectId: null,
});
