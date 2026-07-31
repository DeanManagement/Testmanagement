import { Injectable, inject } from '@angular/core';
import { Actions, createEffect, ofType } from '@ngrx/effects';
import { Router } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';
import { of } from 'rxjs';
import { catchError, map, mergeMap, tap } from 'rxjs/operators';
import { ProjectApiService } from '../../core/services/project-api.service';
import { ProjectActions } from './project.actions';

@Injectable()
export class ProjectEffects {
  private readonly actions$ = inject(Actions);
  private readonly projectApi = inject(ProjectApiService);
  private readonly router = inject(Router);
  private readonly snackBar = inject(MatSnackBar);
  private readonly translate = inject(TranslateService);

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
        tap(() => this.snackBar.open(this.translate.instant('common.savedSuccessfully'), this.translate.instant('common.close'), { duration: 3000 })),
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

  updateProjectSnackbar$ = createEffect(
    () =>
      this.actions$.pipe(
        ofType(ProjectActions.updateProjectSuccess),
        tap(() => this.snackBar.open(this.translate.instant('common.savedSuccessfully'), this.translate.instant('common.close'), { duration: 3000 }))
      ),
    { dispatch: false }
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
        tap(() => this.snackBar.open(this.translate.instant('common.deletedSuccessfully'), this.translate.instant('common.close'), { duration: 3000 })),
        tap(() => this.router.navigate(['/projects']))
      ),
    { dispatch: false }
  );
}
