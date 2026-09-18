import { describe, expect, it } from 'vitest';
import { ProjectEnvironment } from '../../shared/models/environment.model';
import { sortOrderChanges } from './environment-order';

function env(id: string, sortOrder: number): ProjectEnvironment {
  return { id, name: id, description: null, sortOrder, archived: false, runCount: 0, bugCount: 0 };
}

describe('sortOrderChanges', () => {
  it('should save only the environments whose position changed', () => {
    // b dragged above a; c stays third.
    expect(sortOrderChanges([env('b', 1), env('a', 0), env('c', 2)]))
      .toEqual([{ id: 'b', sortOrder: 0 }, { id: 'a', sortOrder: 1 }]);
  });

  it('should normalise gaps left by deletions or backfill', () => {
    expect(sortOrderChanges([env('a', 0), env('b', 5)])).toEqual([{ id: 'b', sortOrder: 1 }]);
  });

  it('should save nothing when the order is unchanged', () => {
    expect(sortOrderChanges([env('a', 0), env('b', 1)])).toEqual([]);
  });
});
