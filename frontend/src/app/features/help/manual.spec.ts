import { describe, expect, it } from 'vitest';
import deManual from '../../../assets/manual/de.json';
import enManual from '../../../assets/manual/en.json';
import { Manual, MyCapabilities, chaptersFor } from './manual.model';

/**
 * Who sees which chapter.
 *
 * <p>Getting this wrong is not a security hole — every action is still authorized on the server —
 * but it is the whole point of the feature. Showing a viewer six chapters of instructions they
 * cannot follow is the thing this replaces, and hiding the administration chapter from the
 * administrator who came looking for it is the opposite failure.
 */
describe('manual, tailored to the reader', () => {
  const manual = enManual as Manual;

  const caps = (over: Partial<MyCapabilities> = {}): MyCapabilities => ({
    systemAdmin: false,
    projectRoles: [],
    projectMemberships: 1,
    ...over,
  });

  const idsFor = (c: MyCapabilities | null) => chaptersFor(manual, c).map((chapter) => chapter.id);

  it('shows a viewer the shared chapters only', () => {
    const ids = idsFor(caps({ projectRoles: ['VIEWER'], highestRole: 'VIEWER' }));

    expect(ids).toContain('erste-schritte');
    expect(ids).toContain('testfaelle-lesen');
    expect(ids).not.toContain('testfaelle-schreiben');
    expect(ids).not.toContain('projekt-verwalten');
    expect(ids).not.toContain('instanz-verwalten');
  });

  it('shows a tester the authoring chapters but not administration', () => {
    const ids = idsFor(caps({ projectRoles: ['TESTER'], highestRole: 'TESTER' }));

    expect(ids).toContain('testfaelle-schreiben');
    expect(ids).toContain('durchfuehren');
    expect(ids).toContain('organisieren');
    expect(ids).not.toContain('projekt-verwalten');
    expect(ids).not.toContain('instanz-verwalten');
  });

  it('gives a project admin the tester chapters too', () => {
    // An admin can do everything a tester can, so withholding the authoring chapters from them
    // would be an odd reading of "tailored".
    const ids = idsFor(caps({ projectRoles: ['ADMIN'], highestRole: 'ADMIN' }));

    expect(ids).toContain('testfaelle-schreiben');
    expect(ids).toContain('projekt-verwalten');
    expect(ids).not.toContain('instanz-verwalten');
  });

  it('shows an instance admin everything', () => {
    const ids = idsFor(caps({ systemAdmin: true, projectRoles: ['ADMIN'], highestRole: 'ADMIN' }));

    expect(ids).toEqual(manual.chapters.map((chapter) => chapter.id));
  });

  it('falls back to the shared chapters when capabilities are unknown', () => {
    // The page renders even if the capabilities call failed; it must not guess upwards.
    const ids = idsFor(null);

    expect(ids).toContain('erste-schritte');
    expect(ids).not.toContain('testfaelle-schreiben');
    expect(ids).not.toContain('instanz-verwalten');
  });

  it('always explains roles, so a short manual does not look broken', () => {
    expect(idsFor(caps({ projectRoles: [] }))).toContain('rollen');
  });

  it('keeps both languages structurally identical', () => {
    // The chapters are written per language rather than key-by-key, so nothing else would catch a
    // chapter added to one and forgotten in the other — or an audience that drifted apart, which
    // would show a German reader a chapter an English reader cannot see.
    const de = deManual as Manual;
    const en = enManual as Manual;

    expect(de.chapters.map((c) => c.id)).toEqual(en.chapters.map((c) => c.id));
    de.chapters.forEach((chapter, i) => {
      expect(chapter.audiences).toEqual(en.chapters[i].audiences);
      expect(chapter.sections.length).toBe(en.chapters[i].sections.length);
    });
  });

  it('has no chapter without an audience, which would be invisible to everyone', () => {
    (deManual as Manual).chapters.forEach((chapter) => {
      expect(chapter.audiences.length).toBeGreaterThan(0);
    });
  });
});
