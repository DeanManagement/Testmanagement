import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { describe, expect, it, vi } from 'vitest';
import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor', () => {
  function failWith(status: number, body: { message: string } | null): ReturnType<typeof vi.fn> {
    const open = vi.fn();
    TestBed.configureTestingModule({
      imports: [TranslateModule.forRoot()],
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: { open } },
      ],
    });
    TestBed.inject(HttpClient).post('/api/build-servers/s1/test', {}).subscribe({ error: () => undefined });
    TestBed.inject(HttpTestingController).expectOne('/api/build-servers/s1/test')
      .flush(body, { status, statusText: 'x' });
    return open;
  }

  describe('when an external service failed (424)', () => {
    it('shows the reason the backend reported', () => {
      const open = failWith(424, { message: 'Azure DevOps rejected the token (401)' });

      expect(open).toHaveBeenCalledWith('Azure DevOps rejected the token (401)', expect.anything(), expect.anything());
    });

    it('falls back to the generic server error without a reason', () => {
      const open = failWith(424, null);

      expect(open).toHaveBeenCalledWith('errors.server', expect.anything(), expect.anything());
    });
  });
});
