import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateModule } from '@ngx-translate/core';
import { beforeEach, describe, expect, it } from 'vitest';
import { StepSpecCardComponent } from './step-spec-card.component';

/** PRD-052: no empty cell beside test data or an image. */
describe('StepSpecCardComponent', () => {
  let fixture: ComponentFixture<StepSpecCardComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [StepSpecCardComponent, TranslateModule.forRoot()],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(StepSpecCardComponent);
    fixture.componentRef.setInput('action', 'Log in');
    fixture.componentRef.setInput('expectedResult', 'Dashboard');
  });

  function tile(id: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-test-id="${id}"]`);
  }

  it('spans test data across the card when there is no image', () => {
    fixture.componentRef.setInput('testData', 'user@example.com');
    fixture.detectChanges();

    expect(tile('step-spec-test-data')?.classList).toContain('spec-tile--wide');
    expect(tile('step-spec-image')).toBeNull();
  });

  it('puts test data and the image side by side when both exist', () => {
    fixture.componentRef.setInput('testData', 'user@example.com');
    fixture.componentRef.setInput('imageUrl', 'data:image/png;base64,iVBORw0KGgo=');
    fixture.detectChanges();

    expect(tile('step-spec-test-data')?.classList).not.toContain('spec-tile--wide');
    expect(tile('step-spec-image')?.classList).not.toContain('spec-tile--wide');
  });

  it('adds no row when neither exists', () => {
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('.spec-tile').length).toBe(2);
  });
});
