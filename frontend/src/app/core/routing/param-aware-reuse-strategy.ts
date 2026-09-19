import { Injectable } from '@angular/core';
import { ActivatedRouteSnapshot, BaseRouteReuseStrategy, Params } from '@angular/router';

/**
 * Reuses a route's component only when both its config and its path parameters are unchanged.
 *
 * Angular's default reuses the component whenever the config matches, so going from run A to run B
 * (search, a link, the back button) kept the same component instance. Every detail page reads its
 * id once from the snapshot, so the URL changed while the page kept showing A and made no request
 * (bug report 971382dc). Recreating the component here fixes all of them at once.
 *
 * Query parameters are left out on purpose: list filters live in them, and a filter change must
 * not rebuild the page.
 */
@Injectable()
export class ParamAwareReuseStrategy extends BaseRouteReuseStrategy {
  override shouldReuseRoute(future: ActivatedRouteSnapshot, current: ActivatedRouteSnapshot): boolean {
    return future.routeConfig === current.routeConfig && sameParams(future.params, current.params);
  }
}

function sameParams(a: Params, b: Params): boolean {
  const keys = Object.keys(a);
  return keys.length === Object.keys(b).length && keys.every((key) => a[key] === b[key]);
}
