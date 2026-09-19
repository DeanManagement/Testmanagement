import { describe, expect, it } from 'vitest';
import { durationTranslation, effortOf, millisToMinutes } from './duration';

describe('duration', () => {
  describe('durationTranslation', () => {
    it('should show minutes alone under an hour', () => {
      expect(durationTranslation(45)).toEqual({ key: 'duration.minutes', params: { hours: 0, minutes: 45 } });
    });

    it('should show hours alone on the hour', () => {
      expect(durationTranslation(120)).toEqual({ key: 'duration.hours', params: { hours: 2, minutes: 0 } });
    });

    it('should show both parts otherwise', () => {
      expect(durationTranslation(200)).toEqual({ key: 'duration.hoursMinutes', params: { hours: 3, minutes: 20 } });
    });

    it('should show zero as zero minutes', () => {
      expect(durationTranslation(0)).toEqual({ key: 'duration.minutes', params: { hours: 0, minutes: 0 } });
    });
  });

  describe('millisToMinutes', () => {
    it('should round to whole minutes', () => {
      expect(millisToMinutes(150_000)).toBe(3);
    });

    it('should never show a real, short duration as zero', () => {
      expect(millisToMinutes(4_000)).toBe(1);
      expect(millisToMinutes(0)).toBe(0);
    });
  });

  describe('effortOf', () => {
    it('should match the server: remaining counts pending estimates, unestimated pending counted apart', () => {
      const effort = effortOf([
        { status: 'PASSED', estimateMinutes: 30, durationMs: 1_800_000 },
        { status: 'PENDING', estimateMinutes: 20, durationMs: null },
        { status: 'PENDING', estimateMinutes: null, durationMs: null },
        { status: 'PENDING', estimateMinutes: 10, durationMs: 600_000 },
      ]);

      expect(effort).toEqual({ estimatedMinutes: 60, remainingMinutes: 30, actualMinutes: 40, pendingUnestimated: 1 });
    });
  });
});
