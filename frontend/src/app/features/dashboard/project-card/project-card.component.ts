import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';
import { Project } from '../../../shared/models/project.model';

@Component({
  selector: 'app-project-card',
  standalone: true,
  imports: [RouterLink, MatButtonModule, MatIconModule, TranslateModule],
  templateUrl: './project-card.component.html',
  styleUrl: './project-card.component.scss',
})
export class ProjectCardComponent {
  @Input({ required: true }) project!: Project;
}
