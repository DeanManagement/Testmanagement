import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { catchError, map, mergeMap, tap } from 'rxjs/operators';
import { ProjectApiService } from '../../core/services/project-api.service';
import { ProjectActions } from './project.actions';

@Injectable()
export class ProjectEffects {
  private readonly actions$ = inject(Actions);
  private readonly projectApi = inject(ProjectApiService);
  private readonly router = inject(Router);

  loadProjects$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ProjectActions.loadProjects),
      mergeMap(() =>
        this.projectApi.getAll().pipe(
          map((projects) => ProjectActions.loadProjectsSuccess({ projects })),
          catchError((error) =>
            of(ProjectActions.loadProjectsFailure({ error: error.message }))
          )
        )
      )
    )
  );

  createProject$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ProjectActions.createProject),
      mergeMap(({ request }) =>
        this.projectApi.create(request).pipe(
          map((project) => ProjectActions.createProjectSuccess({ project })),
          catchError((error) =>
            of(ProjectActions.createProjectFailure({ error: error.message }))
          )
        )
      )
    )
  );

  createProjectSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(ProjectActions.createProjectSuccess),
        tap(({ project }) => this.router.navigate(['/projects', project.id]))
      ),
    { dispatch: false }
  );

  updateProject$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ProjectActions.updateProject),
      mergeMap(({ id, request }) =>
        this.projectApi.update(id, request).pipe(
          map((project) => ProjectActions.updateProjectSuccess({ project })),
          catchError((error) =>
            of(ProjectActions.updateProjectFailure({ error: error.message }))
          )
        )
      )
    )
  );

  deleteProject$ = createEffect(() =>
    this.actions$.pipe(
      ofType(ProjectActions.deleteProject),
      mergeMap(({ id }) =>
        this.projectApi.delete(id).pipe(
          map(() => ProjectActions.deleteProjectSuccess({ id })),
          catchError((error) =>
            of(ProjectActions.deleteProjectFailure({ error: error.message }))
          )
        )
      )
    )
  );

  deleteProjectSuccess$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(ProjectActions.deleteProjectSuccess),
        tap(() => this.router.navigate(['/projects']))
      ),
    { dispatch: false }
  );
}
