import { Pipe, PipeTransform, inject, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { BehaviorSubject, Observable } from 'rxjs';

@Pipe({
  name: 'authImage',
  standalone: true,
  pure: false,
})
export class AuthImagePipe implements PipeTransform, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly sanitizer = inject(DomSanitizer);

  private currentUrl: string | null = null;
  private blobUrl: string | null = null;
  private result$ = new BehaviorSubject<SafeUrl | string>('');
  private loading = false;

  transform(url: string | null | undefined): SafeUrl | string {
    if (!url) return '';

    if (url === this.currentUrl) {
      return this.result$.value;
    }

    this.cleanup();
    this.currentUrl = url;

    if (url.startsWith('data:')) {
      this.result$.next(url);
      return url;
    }

    if (!this.loading) {
      this.loading = true;
      this.http.get(url, { responseType: 'blob' }).subscribe({
        next: (blob) => {
          this.blobUrl = URL.createObjectURL(blob);
          const safe = this.sanitizer.bypassSecurityTrustUrl(this.blobUrl);
          this.result$.next(safe);
          this.loading = false;
        },
        error: () => {
          this.loading = false;
        },
      });
    }

    return this.result$.value;
  }

  ngOnDestroy(): void {
    this.cleanup();
  }

  private cleanup(): void {
    if (this.blobUrl) {
      URL.revokeObjectURL(this.blobUrl);
      this.blobUrl = null;
    }
  }
}
