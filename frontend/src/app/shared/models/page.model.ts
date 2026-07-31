/**
 * Spring Data `PagedModel` shape returned by paginated list endpoints:
 * `{ content: [...], page: { size, number, totalElements, totalPages } }`.
 */
export interface Page<T> {
  content: T[];
  page: PageMetadata;
}

export interface PageMetadata {
  size: number;
  number: number;
  totalElements: number;
  totalPages: number;
}

export const EMPTY_PAGE_METADATA: PageMetadata = {
  size: 50,
  number: 0,
  totalElements: 0,
  totalPages: 0,
};
