import { Component, Input } from '@angular/core';
import { TranslateModule } from '@ngx-translate/core';
import { AuthImagePipe } from '../../pipes/auth-image.pipe';

@Component({
  selector: 'app-step-spec-card',
  standalone: true,
  imports: [TranslateModule, AuthImagePipe],
  templateUrl: './step-spec-card.component.html',
  styleUrl: './step-spec-card.component.scss',
})
export class StepSpecCardComponent {
  @Input({ required: true }) action!: string;
  @Input({ required: true }) expectedResult!: string;
  @Input() testData: string | null = null;
  @Input() imageUrl: string | null = null;
}
