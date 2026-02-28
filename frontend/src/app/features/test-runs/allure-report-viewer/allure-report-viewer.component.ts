import { ChangeDetectorRef, Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { TranslateModule } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { TestRunApiService } from '../../../core/services/test-run-api.service';
import { AuthService } from '../../../core/services/auth.service';
import { selectTestRunById } from '../../../store/test-run/test-run.selectors';
import { TestRunActions } from '../../../store/test-run/test-run.actions';
import { first } from 'rxjs/operators';

@Component({
  selector: 'app-allure-report-viewer',
  standalone: true,
  imports: [
    MatButtonModule,
    MatIconModule,
    MatToolbarModule,
    TranslateModule,
  ],
  templateUrl: './allure-report-viewer.component.html',
  styleUrl: './allure-report-viewer.component.scss',
})
export class AllureReportViewerComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly sanitizer = inject(DomSanitizer);
  private readonly testRunApi = inject(TestRunApiService);
  private readonly authService = inject(AuthService);
  private readonly store = inject(Store);
  private readonly cdr = inject(ChangeDetectorRef);

  iframeSrc: SafeResourceUrl | null = null;
  projectId = '';
  runId = '';

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.runId = this.route.snapshot.paramMap.get('runId') ?? '';

    this.store.dispatch(TestRunActions.loadTestRun({ projectId: this.projectId, id: this.runId }));
    this.store.select(selectTestRunById(this.runId)).pipe(
      first(run => !!run),
    ).subscribe(run => {
      if (run) {
        const baseUrl = this.testRunApi.getAllureReportViewUrl(this.projectId, run.key);
        const token = this.authService.getAccessToken();
        const url = token ? `${baseUrl}?token=${token}` : baseUrl;
        this.iframeSrc = this.sanitizer.bypassSecurityTrustResourceUrl(url);
        this.cdr.detectChanges();
      }
    });
  }

  goBack(): void {
    this.router.navigate(['/projects', this.projectId, 'test-runs', this.runId]);
  }
}
