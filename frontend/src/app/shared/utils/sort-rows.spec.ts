import { describe, expect, it } from 'vitest';
import { sortRows } from './sort-rows';

describe('sortRows', () => {
  const rows = [{ key: 'P-Run-10', n: 3 }, { key: 'P-Run-2', n: null }, { key: 'P-Run-1', n: 1 }];
  const value = (row: { key: string; n: number | null }, column: string) =>
    column === 'key' ? row.key : row.n;

  it('orders keys naturally, so 10 follows 2', () => {
    expect(sortRows(rows, { active: 'key', direction: 'asc' }, value).map((r) => r.key))
      .toEqual(['P-Run-1', 'P-Run-2', 'P-Run-10']);
  });

  it('reverses for descending', () => {
    expect(sortRows(rows, { active: 'key', direction: 'desc' }, value).map((r) => r.key))
      .toEqual(['P-Run-10', 'P-Run-2', 'P-Run-1']);
  });

  it('puts empty values last in both directions', () => {
    expect(sortRows(rows, { active: 'n', direction: 'asc' }, value).map((r) => r.n)).toEqual([1, 3, null]);
    expect(sortRows(rows, { active: 'n', direction: 'desc' }, value).map((r) => r.n)).toEqual([3, 1, null]);
  });

  it('leaves the order alone when no sort is set', () => {
    expect(sortRows(rows, { active: 'key', direction: '' }, value)).toBe(rows);
  });
});
