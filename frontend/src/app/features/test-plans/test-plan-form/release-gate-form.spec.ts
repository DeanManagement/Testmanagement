import { describe, expect, it } from 'vitest';
import { hasCriteria, toGate } from './release-gate-form';

describe('release gate form', () => {
  describe('toGate', () => {
    it('should send empty inputs as null, so the criterion is off', () => {
      expect(toGate({ minPassRate: '', maxBlockerBugs: null, minCoverage: undefined, maxFlaky: '' }))
        .toEqual({ minPassRate: null, maxBlockerBugs: null, minCoverage: null, maxFlaky: null });
    });

    it('should keep zero, which is a real threshold', () => {
      expect(toGate({ minPassRate: 98.5, maxBlockerBugs: 0, minCoverage: null, maxFlaky: '2' }))
        .toEqual({ minPassRate: 98.5, maxBlockerBugs: 0, minCoverage: null, maxFlaky: 2 });
    });
  });

  describe('hasCriteria', () => {
    it('should be false without a gate or with every threshold off', () => {
      expect(hasCriteria(undefined)).toBe(false);
      expect(hasCriteria({ minPassRate: null, maxBlockerBugs: null, minCoverage: null, maxFlaky: null })).toBe(false);
    });

    it('should be true once any threshold is set, zero included', () => {
      expect(hasCriteria({ minPassRate: null, maxBlockerBugs: 0, minCoverage: null, maxFlaky: null })).toBe(true);
    });
  });
});
