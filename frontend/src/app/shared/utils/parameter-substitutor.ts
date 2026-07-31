/**
 * Mirror of the backend `ParameterSubstitutor` (PRD-015 §3.2), used for the live preview in the
 * editor. The pattern and the unknown-placeholder rule must stay identical, or the preview would
 * promise something the execution screen does not deliver.
 */
const PLACEHOLDER = /\{([A-Za-z0-9_.-]+)}/g;

export function substitute(text: string | null | undefined, values: Record<string, string>): string {
  if (!text) {
    return text ?? '';
  }
  // An unknown placeholder is left literal rather than blanked: a step reading "enter " looks
  // finished and is quietly wrong, which is worse than one that visibly still has a gap.
  return text.replace(PLACEHOLDER, (match, key: string) =>
    Object.prototype.hasOwnProperty.call(values, key) ? values[key] : match,
  );
}

export function placeholdersIn(text: string | null | undefined): string[] {
  if (!text) {
    return [];
  }
  const keys = new Set<string>();
  for (const match of text.matchAll(PLACEHOLDER)) {
    keys.add(match[1]);
  }
  return [...keys];
}

export function unresolvedIn(text: string | null | undefined, values: Record<string, string>): string[] {
  return placeholdersIn(text).filter(
    (key) => !Object.prototype.hasOwnProperty.call(values, key),
  );
}
