import { Pipe, PipeTransform } from '@angular/core';
import { Marked, Renderer, Tokens } from 'marked';

const HTML_ESCAPES: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };

function escapeHtml(text: string): string {
  return text.replace(/[&<>"']/g, (ch) => HTML_ESCAPES[ch]);
}

/*
 * GFM with single newlines as line breaks, so text written as plain lines keeps its layout.
 * Raw HTML in the source is shown as text, never parsed; links open in a new tab. The result
 * still goes through Angular's sanitizer via [innerHTML], which strips javascript: URLs.
 */
const markdown = new Marked({
  gfm: true,
  breaks: true,
  async: false,
  renderer: {
    html({ text, block }: Tokens.HTML | Tokens.Tag): string {
      return block ? `<p>${escapeHtml(text)}</p>` : escapeHtml(text);
    },
    link(token: Tokens.Link): string {
      return Renderer.prototype.link.call(this, token).replace(/^<a /, '<a target="_blank" rel="noopener noreferrer" ');
    },
  },
});

/**
 * User- or agent-written Markdown (descriptions, preconditions, step texts) as HTML for
 * `[innerHTML]` inside a `.tm-markdown` element (TM03). Edit forms keep the plain source.
 */
@Pipe({ name: 'markdown', standalone: true })
export class MarkdownPipe implements PipeTransform {
  transform(value: string | null | undefined): string {
    return value ? (markdown.parse(value) as string) : '';
  }
}
