import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { RatePipe } from './rate.pipe';

describe('RatePipe', () => {
  const pipe = () => TestBed.runInInjectionContext(() => new RatePipe());

  it('shows no figure as a dash, not 0 %', () => {
    expect(pipe().transform(null)).toBe('–');
  });

  it('shows a figure with one decimal', () => {
    expect(pipe().transform(0)).toBe('0.0%');
    expect(pipe().transform(10.476)).toBe('10.5%');
  });
});
