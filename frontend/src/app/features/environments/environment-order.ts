import { ProjectEnvironment } from '../../shared/models/environment.model';

export interface SortOrderChange {
  id: string;
  sortOrder: number;
}

/**
 * After a drag, each environment's sort order becomes its position; only the ones that moved
 * need saving, which keeps a reorder at one or two requests in the common case.
 */
export function sortOrderChanges(ordered: readonly ProjectEnvironment[]): SortOrderChange[] {
  return ordered
    .map((environment, index) => ({ id: environment.id, sortOrder: index, previous: environment.sortOrder }))
    .filter((change) => change.sortOrder !== change.previous)
    .map(({ id, sortOrder }) => ({ id, sortOrder }));
}
