import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { TestCaseApiService } from './test-case-api.service';

describe('TestCaseApiService', () => {
  let api: TestCaseApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    api = TestBed.inject(TestCaseApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('exportSpecification (TM12)', () => {
    it('sends the selected ids and nothing of the filter when cases are selected', () => {
      api.exportSpecification('p1', { q: 'login', folderId: 'f1' }, ['a', 'b']).subscribe();

      const request = http.expectOne((r) => r.url === '/api/projects/p1/test-cases/specification');
      expect(request.request.params.getAll('ids')).toEqual(['a', 'b']);
      expect(request.request.params.has('q')).toBe(false);
      request.flush(new Blob());
    });

    it('sends the list filter, without paging, when nothing is selected', () => {
      api.exportSpecification('p1', { q: 'login', folderId: 'f1', includeSubfolders: true, page: 3, size: 50 }, [])
        .subscribe();

      const request = http.expectOne((r) => r.url === '/api/projects/p1/test-cases/specification');
      expect(request.request.params.get('q')).toBe('login');
      expect(request.request.params.get('folderId')).toBe('f1');
      expect(request.request.params.get('includeSubfolders')).toBe('true');
      expect(request.request.params.has('page')).toBe(false);
      request.flush(new Blob());
    });
  });
});
