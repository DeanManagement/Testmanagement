import { TestStepRequest } from '../../../shared/models/test-case.model';

/**
 * Step keywords the join recognises: English and German, the languages the app ships in. A step
 * starting with any other word is written with the catch-all keyword `*`.
 */
const ENGLISH_KEYWORDS = ['Given', 'When', 'Then', 'And', 'But'];
const GERMAN_KEYWORDS = ['Angenommen', 'Gegeben sei', 'Gegeben seien', 'Wenn', 'Dann', 'Und', 'Aber'];
const INDENT = '  ';
const DOC_STRING = '"""';

export interface GherkinSource {
  title: string;
  description: string | null;
  labels: string[];
  steps: TestStepRequest[];
}

/** The case as one scenario, for the form's "Edit as Gherkin" (PRD-040 §3.7). Expected results have no Gherkin form. */
export function toGherkin(source: GherkinSource): string {
  const lines: string[] = [];
  const tags = source.labels.map((label) => '@' + label.trim().replace(/\s+/g, '-')).filter((tag) => tag.length > 1);
  if (tags.length > 0) {
    lines.push(tags.join(' '));
  }
  // German steps under an English "Scenario:" would be read as its description, not as steps.
  const german = isGerman(source.steps.map((step) => oneLine(step.action)));
  if (german) {
    lines.unshift('# language: de');
  }
  lines.push(`${german ? 'Szenario' : 'Scenario'}: ${oneLine(source.title)}`);
  for (const line of (source.description ?? '').split('\n')) {
    if (line.trim()) {
      lines.push(INDENT + line);
    }
  }
  for (const step of source.steps) {
    const action = oneLine(step.action);
    const keywords = german ? GERMAN_KEYWORDS : ENGLISH_KEYWORDS;
    lines.push(INDENT + (startsWithKeyword(action, keywords) ? action : `* ${action}`));
    if (step.testData) {
      lines.push(...argumentLines(step.testData));
    }
  }
  return lines.join('\n') + '\n';
}

export function isKeywordStep(action: string): boolean {
  return startsWithKeyword(action, ENGLISH_KEYWORDS) || startsWithKeyword(action, GERMAN_KEYWORDS);
}

/** German when some step uses a German keyword and none an English one. */
function isGerman(actions: string[]): boolean {
  return actions.some((a) => startsWithKeyword(a, GERMAN_KEYWORDS))
    && !actions.some((a) => startsWithKeyword(a, ENGLISH_KEYWORDS));
}

function startsWithKeyword(action: string, keywords: string[]): boolean {
  return keywords.some((keyword) => action.startsWith(keyword + ' ') && action.length > keyword.length + 1);
}

/** A pipe table stays a table; anything else is a doc string. */
function argumentLines(testData: string): string[] {
  const rows = testData.split('\n');
  const indent = INDENT.repeat(2);
  if (rows.every((row) => /^\s*\|.*\|\s*$/.test(row))) {
    return rows.map((row) => indent + row.trim());
  }
  return [indent + DOC_STRING, ...rows.map((row) => indent + row), indent + DOC_STRING];
}

function oneLine(text: string): string {
  return (text ?? '').trim().replace(/\s*\n\s*/g, ' ');
}
