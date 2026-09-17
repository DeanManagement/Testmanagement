import { Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { Store } from '@ngrx/store';
import { TranslateModule } from '@ngx-translate/core';
import { Language, LanguageService } from '../../core/services/language.service';
import { selectIsAuthenticated } from '../../store/auth/auth.selectors';

@Component({
  selector: 'app-landing',
  standalone: true,
  imports: [
    RouterLink,
    MatButtonModule,
    MatIconModule,
    MatMenuModule,
    TranslateModule,
  ],
  templateUrl: './landing.component.html',
  styleUrl: './landing.component.scss',
})
export class LandingComponent {
  private readonly languageService = inject(LanguageService);

  /**
   * The landing page is public, so it is also what a signed-in user sees at `/`. It used to ignore
   * the session and offer "Sign In" regardless, which then skipped the login form entirely.
   */
  protected readonly isAuthenticated = toSignal(inject(Store).select(selectIsAuthenticated), { initialValue: false });

  switchLanguage(language: Language): void {
    this.languageService.use(language);
  }
}
