import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { TestSuiteActions } from '../../../store/test-suite/test-suite.actions';
import { selectTestSuiteById } from '../../../store/test-suite/test-suite.selectors';
import { TestSuite } from '../../../shared/models/test-suite.model';

@Component({
  selector: 'app-test-suite-detail',
  standalone: true,
  imports: [
    AsyncPipe,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatListModule,
    TranslateModule,
  ],
  templateUrl: './test-suite-detail.component.html',
  styleUrl: './test-suite-detail.component.scss',
})
export class TestSuiteDetailComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);

  projectId = '';
  testSuite$: Observable<TestSuite | undefined> = of(undefined);

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    const id = this.route.snapshot.paramMap.get('suiteId');
    if (this.projectId && id) {
      this.store.dispatch(TestSuiteActions.loadTestSuites({ projectId: this.projectId }));
      this.testSuite$ = this.store.select(selectTestSuiteById(id));
    }
  }

  deleteTestSuite(id: string): void {
    this.store.dispatch(TestSuiteActions.deleteTestSuite({ projectId: this.projectId, id }));
  }
}
