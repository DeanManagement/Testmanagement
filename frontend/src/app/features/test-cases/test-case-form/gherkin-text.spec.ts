import { isKeywordStep, toGherkin } from './gherkin-text';

describe('toGherkin', () => {
  it('writes the title, tags, description and keyword steps as a scenario', () => {
    const text = toGherkin({
      title: 'Valid login',
      description: 'Happy path',
      labels: ['smoke', 'needs review'],
      steps: [
        { action: 'Given a user', expectedResult: '' },
        { action: 'When they sign in', expectedResult: '' },
      ],
    });

    expect(text).toBe('@smoke @needs-review\nScenario: Valid login\n  Happy path\n  Given a user\n  When they sign in\n');
  });

  it('writes a step without a keyword with the catch-all keyword', () => {
    const text = toGherkin({ title: 'T', description: null, labels: [], steps: [{ action: 'Open the page', expectedResult: 'Loads' }] });

    expect(text).toContain('  * Open the page\n');
    expect(text).not.toContain('Loads');
  });

  it('keeps a pipe table as a table and anything else as a doc string', () => {
    const text = toGherkin({
      title: 'T', description: null, labels: [],
      steps: [
        { action: 'Given users', expectedResult: '', testData: '| name |\n| Ada |' },
        { action: 'When I post', expectedResult: '', testData: '{"a": 1}' },
      ],
    });

    expect(text).toContain('  Given users\n    | name |\n    | Ada |\n');
    expect(text).toContain('  When I post\n    """\n    {"a": 1}\n    """\n');
  });
});

describe('toGherkin with German steps', () => {
  it('writes a language header first and German keywords, so the steps are read as steps', () => {
    const text = toGherkin({
      title: 'Gültig', description: null, labels: ['smoke'],
      steps: [{ action: 'Wenn er sich anmeldet', expectedResult: '' }, { action: 'Seite prüfen', expectedResult: '' }],
    });

    expect(text).toBe('# language: de\n@smoke\nSzenario: Gültig\n  Wenn er sich anmeldet\n  * Seite prüfen\n');
  });
});

describe('isKeywordStep', () => {
  it('needs the keyword as a whole word followed by text', () => {
    expect(isKeywordStep('Given a user')).toBe(true);
    expect(isKeywordStep('Angenommen ein Benutzer')).toBe(true);
    expect(isKeywordStep('Givenness')).toBe(false);
    expect(isKeywordStep('Given ')).toBe(false);
  });
});
