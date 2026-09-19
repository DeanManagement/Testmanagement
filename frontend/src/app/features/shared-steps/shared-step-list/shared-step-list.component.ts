import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { debounceTime, distinctUntilChanged, startWith, switchMap } from 'rxjs';
import { SharedStepApiService } from '../../../core/services/shared-step-api.service';
import { SharedStepSummary } from '../../../shared/models/shared-step.model';
import { LocalizedDatePipe } from '../../../shared/pipes/localized-date.pipe';

const SEARCH_DEBOUNCE_MS = 250;

/** The project's shared steps (PRD-030), with how many test cases use each. */
@Component({
  selector: 'app-shared-step-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatButtonModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    TranslateModule,
    LocalizedDatePipe,
  ],
  templateUrl: './shared-step-list.component.html',
  styleUrl: './shared-step-list.component.scss',
})
export class SharedStepListComponent implements OnInit {
  private readonly api = inject(SharedStepApiService);
  private readonly route = inject(ActivatedRoute);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  projectId = '';
  readonly query = new FormControl('', { nonNullable: true });
  sharedSteps: SharedStepSummary[] = [];
  loading = true;

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.query.valueChanges.pipe(
      startWith(''),
      debounceTime(SEARCH_DEBOUNCE_MS),
      distinctUntilChanged(),
      switchMap((q) => this.api.list(this.projectId, q)),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe((page) => {
      this.sharedSteps = page.content;
      this.loading = false;
      this.cdr.detectChanges();
    });
  }
}
