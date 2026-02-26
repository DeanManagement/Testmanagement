import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTableModule } from '@angular/material/table';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatDialog } from '@angular/material/dialog';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { ProjectActions } from '../../../store/project/project.actions';
import { selectProjectById } from '../../../store/project/project.selectors';
import { selectAuthUser } from '../../../store/auth/auth.selectors';
import { Project } from '../../../shared/models/project.model';
import { ProjectMember, ProjectRole } from '../../../shared/models/project-member.model';
import { ProjectMemberApiService } from '../../../core/services/project-member-api.service';
import { ProjectApiService } from '../../../core/services/project-api.service';
import { AddMemberDialogComponent } from './add-member-dialog/add-member-dialog.component';

@Component({
  selector: 'app-project-detail',
  standalone: true,
  imports: [
    AsyncPipe,
    RouterLink,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatTableModule,
    MatSelectModule,
    MatSlideToggleModule,
    TranslateModule,
  ],
  templateUrl: './project-detail.component.html',
  styleUrl: './project-detail.component.scss',
})
export class ProjectDetailComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);
  private readonly dialog = inject(MatDialog);
  private readonly memberApi = inject(ProjectMemberApiService);
  private readonly projectApi = inject(ProjectApiService);

  project$: Observable<Project | undefined> = of(undefined);
  members: ProjectMember[] = [];
  memberColumns = ['displayName', 'email', 'role', 'actions'];
  isProjectAdmin = false;
  private projectId = '';

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.projectId = id;
      this.store.dispatch(ProjectActions.loadProjects());
      this.project$ = this.store.select(selectProjectById(id));
      this.loadMembers();
    }
  }

  deleteProject(id: string): void {
    this.store.dispatch(ProjectActions.deleteProject({ id }));
  }

  openAddMemberDialog(): void {
    const dialogRef = this.dialog.open(AddMemberDialogComponent, {
      width: '500px',
    });

    dialogRef.afterClosed().subscribe((result) => {
      if (result) {
        this.memberApi.addMember(this.projectId, result).subscribe(() => {
          this.loadMembers();
        });
      }
    });
  }

  updateMemberRole(member: ProjectMember, role: ProjectRole): void {
    this.memberApi.updateRole(this.projectId, member.id, { role }).subscribe(() => {
      this.loadMembers();
    });
  }

  removeMember(memberId: string): void {
    this.memberApi.removeMember(this.projectId, memberId).subscribe(() => {
      this.loadMembers();
    });
  }

  toggleBugReports(enabled: boolean): void {
    this.projectApi.toggleBugReports(this.projectId, enabled).subscribe(() => {
      this.store.dispatch(ProjectActions.loadProjects());
    });
  }

  private loadMembers(): void {
    this.memberApi.getByProject(this.projectId).subscribe((members) => {
      this.members = members;
      this.computeProjectAdmin(members);
    });
  }

  private computeProjectAdmin(members: ProjectMember[]): void {
    this.store.select(selectAuthUser).subscribe((user) => {
      if (!user) {
        this.isProjectAdmin = false;
        return;
      }
      this.isProjectAdmin = user.systemAdmin ||
        members.some(m => m.userId === user.id && m.role === 'ADMIN');
    }).unsubscribe();
  }
}
