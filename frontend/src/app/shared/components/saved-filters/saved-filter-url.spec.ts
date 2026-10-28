import { describe, expect, it } from 'vitest';
import { filterQueryOf, urlWithFilter } from './saved-filter-url';

describe('saved filter URLs', () => {
  describe('filterQueryOf', () => {
    it('should keep every filter parameter, repeated ones included, and drop the page', () => {
      expect(filterQueryOf('/projects/p1/test-cases?status=READY&label=a&label=b&page=3'))
        .toBe('status=READY&label=a&label=b');
    });

    it('should be empty for a list without filters', () => {
      expect(filterQueryOf('/projects/p1/test-cases')).toBe('');
    });
  });

  describe('urlWithFilter', () => {
    it('should replace the current query string with the saved one', () => {
      expect(urlWithFilter('/projects/p1/test-cases?q=old&page=2', 'priority=HIGH'))
        .toBe('/projects/p1/test-cases?priority=HIGH');
    });

    it('should clear the query string for an empty saved filter', () => {
      expect(urlWithFilter('/projects/p1/test-cases?q=old', '')).toBe('/projects/p1/test-cases');
    });
  });
});
