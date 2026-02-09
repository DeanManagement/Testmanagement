import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe, LowerCasePipe } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatTableModule } from '@angular/material/table';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { TestCaseActions } from '../../../store/test-case/test-case.actions';
import { selectTestCaseById } from '../../../store/test-case/test-case.selectors';
import { TestCase } from '../../../shared/models/test-case.model';

@Component({
  selector: 'app-test-case-detail',
  standalone: true,
  imports: [
    AsyncPipe,
    LowerCasePipe,
    RouterLink,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatTableModule,
    TranslateModule,
  ],
  templateUrl: './test-case-detail.component.html',
  styleUrl: './test-case-detail.component.scss',
})
export class TestCaseDetailComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);

  projectId = '';
  testCase$: Observable<TestCase | undefined> = of(undefined);
  stepColumns = ['index', 'action', 'expectedResult'];

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    const id = this.route.snapshot.paramMap.get('tcId');
    if (this.projectId && id) {
      this.store.dispatch(TestCaseActions.loadTestCases({ projectId: this.projectId }));
      this.testCase$ = this.store.select(selectTestCaseById(id));
    }
  }

  deleteTestCase(id: string): void {
    this.store.dispatch(TestCaseActions.deleteTestCase({ projectId: this.projectId, id }));
  }
}
