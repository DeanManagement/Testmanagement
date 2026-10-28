/** Not part of a filter: a saved filter opens on the first page. */
const TRANSIENT_PARAMS = ['page'];

/** The filter part of a list URL: its query string, minus paging, without the leading "?". */
export function filterQueryOf(url: string): string {
  const query = url.split('#')[0].split('?')[1] ?? '';
  const params = new URLSearchParams(query);
  TRANSIENT_PARAMS.forEach((name) => params.delete(name));
  return params.toString();
}

/** The list URL with its query string replaced by a saved filter's. */
export function urlWithFilter(url: string, queryString: string): string {
  const path = url.split('#')[0].split('?')[0];
  return queryString ? `${path}?${queryString}` : path;
}
