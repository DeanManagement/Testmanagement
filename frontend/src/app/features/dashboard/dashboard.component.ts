import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { ProjectCardComponent } from './project-card/project-card.component';
import { MyQueueComponent } from './my-queue/my-queue.component';
import { ProjectActions } from '../../store/project/project.actions';
import { selectAllProjects, selectProjectsLoading } from '../../store/project/project.selectors';
import { selectAuthUser, selectIsSystemAdmin } from '../../store/auth/auth.selectors';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    RouterLink,
    AsyncPipe,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
    TranslateModule,
    ProjectCardComponent,
    MyQueueComponent,
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  private readonly store = inject(Store);

  user$ = this.store.select(selectAuthUser);
  projects$ = this.store.select(selectAllProjects);
  loading$ = this.store.select(selectProjectsLoading);
  isAdmin$ = this.store.select(selectIsSystemAdmin);

  ngOnInit(): void {
    this.store.dispatch(ProjectActions.loadProjects());
  }
}
