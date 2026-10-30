import { TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { TranslateModule } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { CreateCommentRequest } from '../../models/comment.model';
import { CommentFormComponent } from './comment-form.component';

const JANE = { userId: 'u-jane', displayName: 'Jane Doe' };
const JOHN = { userId: 'u-john', displayName: 'John Roe' };

describe('CommentFormComponent', () => {
  beforeEach(() => {
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [CommentFormComponent, TranslateModule.forRoot()],
      providers: [provideNoopAnimations()],
    });
  });

  function render(candidates = [JANE, JOHN]) {
    const fixture = TestBed.createComponent(CommentFormComponent);
    fixture.componentRef.setInput('mentionCandidates', candidates);
    fixture.detectChanges();
    const component = fixture.componentInstance;
    const textarea = (fixture.nativeElement as HTMLElement).querySelector('textarea') as HTMLTextAreaElement;
    const type = (text: string) => {
      component.content = text;
      textarea.value = text;
      textarea.setSelectionRange(text.length, text.length);
      component.onInput();
    };
    const submitted: CreateCommentRequest[] = [];
    component.submitComment.subscribe((request) => submitted.push(request));
    return { component, type, submitted };
  }

  describe('when typing an @', () => {
    it('should suggest the members whose name contains what follows it', () => {
      const { component, type } = render();

      type('Please check @jo');

      expect(component.suggestions).toEqual([JOHN]);
    });

    it('should not suggest anyone for an @ inside a word, like an email address', () => {
      const { component, type } = render();

      type('mail jane@example');

      expect(component.suggestions).toEqual([]);
    });

    it('should not suggest anyone when mentions are off', () => {
      const { component, type } = render([]);

      type('@ja');

      expect(component.suggestions).toEqual([]);
    });
  });

  describe('when a suggestion is picked', () => {
    it('should replace the partial name with the full one', () => {
      const { component, type } = render();
      type('Hi @ja');

      component.pick(JANE);

      expect(component.content).toBe('Hi @Jane Doe ');
    });

    it('should send the picked member as mentioned', () => {
      const { component, type, submitted } = render();
      type('Hi @ja');
      component.pick(JANE);

      component.onSubmit();

      expect(submitted).toEqual([{ content: 'Hi @Jane Doe', mentionedUserIds: ['u-jane'] }]);
    });

    it('should not mention a member whose name was deleted again before sending', () => {
      const { component, type, submitted } = render();
      type('Hi @ja');
      component.pick(JANE);
      component.content = 'Hi all';

      component.onSubmit();

      expect(submitted).toEqual([{ content: 'Hi all' }]);
    });
  });
});
