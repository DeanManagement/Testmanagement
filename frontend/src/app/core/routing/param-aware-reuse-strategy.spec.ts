import { Component, inject } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, RouteReuseStrategy } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { beforeEach, describe, expect, it } from 'vitest';
import { ParamAwareReuseStrategy } from './param-aware-reuse-strategy';

/** Reads its id once, as every detail page does. */
@Component({ selector: 'app-detail', template: '{{ id }}' })
class DetailComponent {
  readonly id = inject(ActivatedRoute).snapshot.paramMap.get('id');
}

describe('ParamAwareReuseStrategy', () => {
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([{ path: 'runs/:id', component: DetailComponent }]),
        { provide: RouteReuseStrategy, useClass: ParamAwareReuseStrategy },
      ],
    });
    harness = await RouterTestingHarness.create();
  });

  it('should build a new page when only the id changes', async () => {
    const first = await harness.navigateByUrl('/runs/A', DetailComponent);

    const second = await harness.navigateByUrl('/runs/B', DetailComponent);

    expect(second).not.toBe(first);
    expect(harness.routeNativeElement?.textContent).toBe('B');
  });

  it('should keep the page when only the query changes', async () => {
    const first = await harness.navigateByUrl('/runs/A', DetailComponent);

    const second = await harness.navigateByUrl('/runs/A?result=r1', DetailComponent);

    expect(second).toBe(first);
  });
});
