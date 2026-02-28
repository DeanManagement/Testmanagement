import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AsyncPipe } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { TranslateModule } from '@ngx-translate/core';
import { TestSuiteActions } from '../../../store/test-suite/test-suite.actions';
import { selectAllTestSuites, selectTestSuitesLoading } from '../../../store/test-suite/test-suite.selectors';
import { TestSuite } from '../../../shared/models/test-suite.model';

@Component({
  selector: 'app-test-suite-list',
  standalone: true,
  imports: [
    AsyncPipe,
    RouterLink,
    FormsModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressSpinnerModule,
    TranslateModule,
  ],
  templateUrl: './test-suite-list.component.html',
  styleUrl: './test-suite-list.component.scss',
})
export class TestSuiteListComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly route = inject(ActivatedRoute);

  projectId = '';
  testSuites$ = this.store.select(selectAllTestSuites);
  loading$ = this.store.select(selectTestSuitesLoading);
  displayedColumns = ['name', 'description', 'testCaseCount', 'actions'];
  searchTerm = '';

  ngOnInit(): void {
    this.projectId = this.route.parent?.snapshot.paramMap.get('id') ?? '';
    if (this.projectId) {
      this.store.dispatch(TestSuiteActions.loadTestSuites({ projectId: this.projectId }));
    }
  }

  filteredTestSuites(testSuites: TestSuite[]): TestSuite[] {
    if (!this.searchTerm) return testSuites;
    const term = this.searchTerm.toLowerCase();
    return testSuites.filter(s =>
      s.name.toLowerCase().includes(term) ||
      s.description?.toLowerCase().includes(term)
    );
  }

  deleteTestSuite(id: string): void {
    this.store.dispatch(TestSuiteActions.deleteTestSuite({ projectId: this.projectId, id }));
  }
}
