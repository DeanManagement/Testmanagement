import { ChangeDetectorRef, Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatToolbarModule } from '@angular/material/toolbar';
import { TranslateModule } from '@ngx-translate/core';
import { Store } from '@ngrx/store';
import { TestRunApiService } from '../../../core/services/test-run-api.service';
import { selectTestRunById } from '../../../store/test-run/test-run.selectors';
import { TestRunActions } from '../../../store/test-run/test-run.actions';
import { first, map, switchMap } from 'rxjs/operators';

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
  private readonly store = inject(Store);
  private readonly cdr = inject(ChangeDetectorRef);
  private readonly destroyRef = inject(DestroyRef);

  iframeSrc: SafeResourceUrl | null = null;
  projectId = '';
  runId = '';

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    this.runId = this.route.snapshot.paramMap.get('runId') ?? '';

    this.store.dispatch(TestRunActions.loadTestRun({ projectId: this.projectId, id: this.runId }));
    // PRD-018: mint a short-lived, single-report view session via the authenticated API,
    // then load the report through the token-in-path URL. The JWT never appears in a URL,
    // and the sandboxed iframe (see template) isolates the report's scripts.
    this.store.select(selectTestRunById(this.runId)).pipe(
      first(run => !!run),
      switchMap(run =>
        this.testRunApi.createAllureViewSession(this.projectId, run!.key).pipe(
          map(session => this.testRunApi.getAllureReportViewUrl(this.projectId, run!.key, session.token)),
        ),
      ),
      takeUntilDestroyed(this.destroyRef),
    ).subscribe(url => {
      this.iframeSrc = this.sanitizer.bypassSecurityTrustResourceUrl(url);
      this.cdr.detectChanges();
    });
  }

  goBack(): void {
    this.router.navigate(['/projects', this.projectId, 'test-runs', this.runId]);
  }
}
