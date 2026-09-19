import { Pipe, PipeTransform, inject } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { durationTranslation } from './duration';

/** Minutes as "3 h 20 min" in the current language (PRD-036). Null reads as an em dash. */
@Pipe({
  name: 'duration',
  standalone: true,
  // Impure for the same reason as localizedDate: the language can change under it.
  pure: false,
})
export class DurationPipe implements PipeTransform {
  private readonly translate = inject(TranslateService);

  transform(minutes: number | null | undefined): string {
    if (minutes == null) {
      return '—';
    }
    const { key, params } = durationTranslation(minutes);
    return this.translate.instant(key, params);
  }
}
