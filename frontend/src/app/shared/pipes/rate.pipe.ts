import { LOCALE_ID, Pipe, PipeTransform, inject } from '@angular/core';
import { formatNumber } from '@angular/common';

/**
 * A pass rate or progress in percent, one decimal (PRD-049). Null means there is no figure, e.g.
 * nothing was executed, and shows as "–": that is not the same as 0 %.
 */
@Pipe({ name: 'rate', standalone: true })
export class RatePipe implements PipeTransform {
  private readonly locale = inject(LOCALE_ID);

  transform(value: number | null | undefined): string {
    return value == null ? '–' : formatNumber(value, this.locale, '1.1-1') + '%';
  }
}
