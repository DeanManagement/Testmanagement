import { Sort } from '@angular/material/sort';

export type SortValue = string | number | null | undefined;

/**
 * Sorts a small, fully loaded table on the client (PRD-052). Text compares naturally, so "Run-10"
 * follows "Run-2"; empty values go last in either direction. Returns a new array.
 */
export function sortRows<T>(rows: T[], sort: Sort, valueOf: (row: T, column: string) => SortValue): T[] {
  if (!sort.active || !sort.direction) {
    return rows;
  }
  const factor = sort.direction === 'asc' ? 1 : -1;
  return [...rows].sort((a, b) => {
    const left = valueOf(a, sort.active);
    const right = valueOf(b, sort.active);
    if (left == null || left === '') return right == null || right === '' ? 0 : 1;
    if (right == null || right === '') return -1;
    return factor * compareValues(left, right);
  });
}

function compareValues(left: string | number, right: string | number): number {
  if (typeof left === 'number' && typeof right === 'number') {
    return left - right;
  }
  return String(left).localeCompare(String(right), undefined, { numeric: true, sensitivity: 'base' });
}
