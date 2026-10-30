/** The list a saved filter belongs to (PRD-053 §3). */
export type SavedFilterView = 'TEST_CASES' | 'BUG_REPORTS';

export interface SavedFilter {
  id: string;
  name: string;
  view: SavedFilterView;
  /** The list's URL query string, without the leading "?". */
  queryString: string;
  shared: boolean;
  mine: boolean;
  /** Owner, or a project ADMIN for a shared filter. */
  editable: boolean;
}

export interface CreateSavedFilterRequest {
  name: string;
  view: SavedFilterView;
  queryString: string;
  shared: boolean;
}

export interface UpdateSavedFilterRequest {
  name: string;
  shared: boolean;
}
