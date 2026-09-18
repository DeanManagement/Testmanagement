/** Client-side environment filter for "my runs", which span projects and so can't use ids (PRD-032). */
interface HasEnvironment {
  environment?: string | null;
}

/** Distinct environment names in use, sorted, ignoring runs without one. */
export function environmentNamesOf(runs: readonly HasEnvironment[]): string[] {
  const names = new Set(runs.map((run) => run.environment).filter((name): name is string => !!name));
  return [...names].sort((a, b) => a.localeCompare(b));
}

/** Runs in the named environment; an empty name means no filter. */
export function filterByEnvironment<T extends HasEnvironment>(runs: readonly T[], name: string): T[] {
  return name ? runs.filter((run) => run.environment === name) : [...runs];
}
