/** What the environment picker offers for the text typed so far (PRD-032). */
export interface EnvironmentOptions {
  /** Existing names containing the typed text, catalogue order kept. */
  matches: string[];
  /** The trimmed text, when it names no existing environment and would be registered on save. */
  newName: string | null;
}

const normalize = (name: string): string => name.trim().toLowerCase();

/** Matches ignore case and surrounding whitespace, the same rule the server applies. */
export function environmentOptions(names: readonly string[], typed: string): EnvironmentOptions {
  const query = normalize(typed);
  const matches = names.filter((name) => normalize(name).includes(query));
  const exists = names.some((name) => normalize(name) === query);
  return { matches, newName: query === '' || exists ? null : typed.trim() };
}
