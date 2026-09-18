import { describe, expect, it } from 'vitest';
import { environmentOptions } from './environment-options';

const NAMES = ['Staging', 'Production', 'Chrome · Staging'];

describe('environmentOptions', () => {
  describe('when nothing is typed', () => {
    it('should offer every environment and nothing new', () => {
      expect(environmentOptions(NAMES, '')).toEqual({ matches: NAMES, newName: null });
    });
  });

  describe('when the text matches part of some names', () => {
    it('should offer those names, ignoring case', () => {
      expect(environmentOptions(NAMES, 'STAG').matches).toEqual(['Staging', 'Chrome · Staging']);
    });
  });

  describe('when the text names an existing environment in another case', () => {
    it('should not offer to add it, because the server will match it', () => {
      expect(environmentOptions(NAMES, ' staging ').newName).toBeNull();
    });
  });

  describe('when the text names no environment', () => {
    it('should offer to add the trimmed name', () => {
      expect(environmentOptions(NAMES, ' QA ')).toEqual({ matches: [], newName: 'QA' });
    });
  });
});
