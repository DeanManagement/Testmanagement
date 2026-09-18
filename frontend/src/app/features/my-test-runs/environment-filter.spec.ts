import { describe, expect, it } from 'vitest';
import { environmentNamesOf, filterByEnvironment } from './environment-filter';

const RUNS = [
  { id: '1', environment: 'staging' },
  { id: '2', environment: 'Production' },
  { id: '3', environment: null },
  { id: '4', environment: 'staging' },
];

describe('environmentNamesOf', () => {
  it('should list each environment once, sorted, without blanks', () => {
    expect(environmentNamesOf(RUNS)).toEqual(['Production', 'staging']);
  });
});

describe('filterByEnvironment', () => {
  it('should keep only runs in the named environment', () => {
    expect(filterByEnvironment(RUNS, 'staging').map((run) => run.id)).toEqual(['1', '4']);
  });

  it('should keep every run when no environment is chosen', () => {
    expect(filterByEnvironment(RUNS, '')).toHaveLength(4);
  });
});
