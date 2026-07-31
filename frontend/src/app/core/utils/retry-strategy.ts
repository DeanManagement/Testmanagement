import { Observable, throwError, timer } from 'rxjs';
import { retry } from 'rxjs/operators';

export function retryWithBackoff(maxRetries = 2, initialDelay = 1000) {
  return <T>(source: Observable<T>) =>
    source.pipe(
      retry({
        count: maxRetries,
        delay: (error, retryCount) => {
          // Only retry on network errors (status 0) or 5xx server errors
          if (error.status && error.status < 500 && error.status !== 0) {
            return throwError(() => error);
          }
          return timer(initialDelay * Math.pow(2, retryCount - 1));
        },
      })
    );
}
