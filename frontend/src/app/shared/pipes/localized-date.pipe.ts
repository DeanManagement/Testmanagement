import { Pipe, PipeTransform, inject } from '@angular/core';
import { formatDate } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';

@Pipe({
  name: 'localizedDate',
  standalone: true,
  pure: false,
})
export class LocalizedDatePipe implements PipeTransform {
  private readonly translate = inject(TranslateService);

  transform(value: string | number | Date | null | undefined, format: string = 'mediumDate'): string {
    if (!value) return '';
    const lang = this.translate.currentLang || this.translate.defaultLang || 'en';
    const locale = lang === 'de' ? 'de-DE' : 'en-US';
    try {
      return formatDate(value, format, locale);
    } catch {
      return String(value);
    }
  }
}
