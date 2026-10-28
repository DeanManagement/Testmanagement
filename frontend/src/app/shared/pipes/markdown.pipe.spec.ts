import { Component, Input } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { MarkdownPipe } from './markdown.pipe';

/** Renders through [innerHTML] exactly like the views do, so Angular's sanitizer is in the loop. */
@Component({
  standalone: true,
  imports: [MarkdownPipe],
  template: `<div class="tm-markdown" [innerHTML]="text | markdown"></div>`,
})
class HostComponent {
  @Input() text: string | null = null;
}

function render(text: string | null): HTMLElement {
  const fixture = TestBed.createComponent(HostComponent);
  fixture.componentRef.setInput('text', text);
  fixture.detectChanges();
  return fixture.nativeElement.querySelector('.tm-markdown');
}

describe('MarkdownPipe', () => {
  it('renders bold text instead of literal asterisks', () => {
    const el = render('**Testziel:** Login prüfen');

    expect(el.querySelector('strong')?.textContent).toBe('Testziel:');
    expect(el.textContent).not.toContain('*');
  });

  it('renders a bullet list', () => {
    const el = render('- one\n- two');

    expect(Array.from(el.querySelectorAll('ul > li')).map((li) => li.textContent)).toEqual(['one', 'two']);
  });

  it('renders a GFM table', () => {
    const el = render('| Field | Value |\n|---|---|\n| user | admin |');

    expect(el.querySelectorAll('table th').length).toBe(2);
    expect(el.querySelector('table td')?.textContent).toBe('user');
  });

  it('keeps single line breaks', () => {
    const el = render('first line\nsecond line');

    expect(el.querySelectorAll('br').length).toBe(1);
  });

  it('opens links in a new tab without leaking the opener', () => {
    const link = render('[docs](https://example.com)').querySelector('a');

    expect(link?.getAttribute('href')).toBe('https://example.com');
    expect(link?.getAttribute('target')).toBe('_blank');
    expect(link?.getAttribute('rel')).toBe('noopener noreferrer');
  });

  it('shows raw HTML as text rather than creating elements', () => {
    const el = render('before <img src=x onerror="alert(1)"> after');

    expect(el.querySelector('img')).toBeNull();
    expect(el.querySelector('[onerror]')).toBeNull();
    expect(el.textContent).toContain('<img src=x onerror="alert(1)">');
  });

  it('shows a raw HTML block as text', () => {
    const el = render('<script>alert(1)</script>');

    expect(el.querySelector('script')).toBeNull();
    expect(el.textContent).toContain('<script>alert(1)</script>');
  });

  it('never leaves a javascript: link clickable', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined); // the sanitizer reports what it stripped

    const link = render('[click](javascript:alert(1))').querySelector('a');
    warn.mockRestore();

    expect(link).not.toBeNull();
    expect(link?.getAttribute('href')).not.toMatch(/^javascript:/i);
  });

  it('renders nothing for an empty value', () => {
    expect(render(null).innerHTML).toBe('');
  });
});
