import { ChangeDetectorRef, Pipe, PipeTransform, inject, OnDestroy } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeUrl } from '@angular/platform-browser';
import { BehaviorSubject } from 'rxjs';

const MAX_CACHE_SIZE = 100;

interface CacheEntry {
  blobUrl: string;
  safeUrl: SafeUrl;
}

const globalCache = new Map<string, CacheEntry>();

function evictOldest(): void {
  if (globalCache.size <= MAX_CACHE_SIZE) return;
  const firstKey = globalCache.keys().next().value!;
  const entry = globalCache.get(firstKey)!;
  URL.revokeObjectURL(entry.blobUrl);
  globalCache.delete(firstKey);
}

@Pipe({
  name: 'authImage',
  standalone: true,
  pure: false,
})
export class AuthImagePipe implements PipeTransform, OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly sanitizer = inject(DomSanitizer);
  // Impure pipes are only re-evaluated when the view is checked. The blob arrives on an HTTP
  // callback, which under zoneless change detection schedules nothing on its own, so without
  // this the image URL is computed but never rendered and the <img> keeps an empty src.
  private readonly cdr = inject(ChangeDetectorRef);

  private currentUrl: string | null = null;
  private localBlobUrl: string | null = null;
  private result$ = new BehaviorSubject<SafeUrl | string>('');
  private loading = false;

  transform(url: string | null | undefined): SafeUrl | string {
    if (!url) return '';

    if (url === this.currentUrl) {
      return this.result$.value;
    }

    this.cleanupLocal();
    this.currentUrl = url;

    if (url.startsWith('data:')) {
      this.result$.next(url);
      return url;
    }

    const cached = globalCache.get(url);
    if (cached) {
      this.result$.next(cached.safeUrl);
      return cached.safeUrl;
    }

    if (!this.loading) {
      this.loading = true;
      this.http.get(url, { responseType: 'blob' }).subscribe({
        next: (blob) => {
          const blobUrl = URL.createObjectURL(blob);
          const safeUrl = this.sanitizer.bypassSecurityTrustUrl(blobUrl);
          globalCache.set(url, { blobUrl, safeUrl });
          evictOldest();
          this.localBlobUrl = null;
          this.result$.next(safeUrl);
          this.loading = false;
          this.cdr.markForCheck();
        },
        error: () => {
          this.loading = false;
        },
      });
    }

    return this.result$.value;
  }

  ngOnDestroy(): void {
    this.cleanupLocal();
  }

  private cleanupLocal(): void {
    if (this.localBlobUrl) {
      URL.revokeObjectURL(this.localBlobUrl);
      this.localBlobUrl = null;
    }
  }
}
