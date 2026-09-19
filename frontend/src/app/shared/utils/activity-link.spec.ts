import { describe, expect, it } from 'vitest';
import { activityRoute } from './activity-link';

describe('activityRoute', () => {
  it('leads to the object page', () => {
    expect(activityRoute('p1', { type: 'TEST_RUN', id: 'r1' })).toEqual(['/projects', 'p1', 'test-runs', 'r1']);
  });

  it('leads to the requirements list, since a requirement has no page of its own', () => {
    expect(activityRoute('p1', { type: 'REQUIREMENT', id: 'q1' })).toEqual(['/projects', 'p1', 'requirements']);
  });

  it('leads nowhere without a link or for a type without a page', () => {
    expect(activityRoute('p1', null)).toBeNull();
    expect(activityRoute('p1', { type: 'PROJECT', id: 'p1' })).toBeNull();
  });
});
