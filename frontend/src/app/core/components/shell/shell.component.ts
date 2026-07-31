import { Component, DestroyRef, HostListener, inject, ViewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { Store } from '@ngrx/store';
import { AsyncPipe } from '@angular/common';
import { map, take } from 'rxjs/operators';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSidenavModule, MatSidenav } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { BreakpointObserver } from '@angular/cdk/layout';
import { AuthService } from '../../services/auth.service';
import { ThemePreference, ThemeService } from '../../services/theme.service';
import { selectAuthUser, selectIsSystemAdmin } from '../../../store/auth/auth.selectors';
import { CommandPaletteComponent } from '../../../shared/components/command-palette/command-palette.component';
import { NotificationBellComponent } from '../notification-bell/notification-bell.component';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    AsyncPipe,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatSidenavModule,
    MatListModule,
    MatMenuModule,
    MatDividerModule,
    MatDialogModule,
    MatTooltipModule,
    TranslateModule,
    NotificationBellComponent,
  ],
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  @ViewChild('sidenav') sidenav!: MatSidenav;

  private readonly translate = inject(TranslateService);
  private readonly router = inject(Router);
  private readonly store = inject(Store);
  private readonly authService = inject(AuthService);
  private readonly themeService = inject(ThemeService);
  private readonly breakpointObserver = inject(BreakpointObserver);
  private readonly dialog = inject(MatDialog);
  private readonly destroyRef = inject(DestroyRef);

  /** Track the currently-open palette so Cmd-K doesn't open two of them. */
  private commandPaletteRef: ReturnType<MatDialog['open']> | null = null;

  user$ = this.store.select(selectAuthUser);
  isAdmin$ = this.store.select(selectIsSystemAdmin);
  isMobile$ = this.breakpointObserver.observe('(max-width: 768px)').pipe(
    map(result => result.matches)
  );

  themePreference = this.themeService.preference;
  resolvedTheme = this.themeService.resolved;

  switchLanguage(lang: string): void {
    this.translate.use(lang);
  }

  setTheme(preference: ThemePreference): void {
    this.themeService.setPreference(preference);
  }

  /** Icon for the toolbar button: reflects what is on screen, not the stored preference. */
  themeIcon(): string {
    return this.resolvedTheme() === 'dark' ? 'dark_mode' : 'light_mode';
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/']);
  }

  closeMobileSidenav(): void {
    if (this.sidenav && this.sidenav.mode === 'over') {
      this.sidenav.close();
    }
  }

  /**
   * Open the global command palette. Bound to Cmd-K (macOS) and Ctrl-K
   * (everywhere else), plus the toolbar search button. Reuses the existing
   * dialog if the palette is already open so a double press doesn't stack
   * two of them.
   */
  openCommandPalette(): void {
    if (this.commandPaletteRef) {
      return;
    }
    this.commandPaletteRef = this.dialog.open(CommandPaletteComponent, {
      width: '560px',
      maxWidth: '90vw',
      position: { top: '14vh' },
      autoFocus: false,
      panelClass: 'command-palette-dialog',
    });
    this.commandPaletteRef.afterClosed().pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.commandPaletteRef = null;
    });
  }

  @HostListener('document:keydown', ['$event'])
  onGlobalKeyDown(event: KeyboardEvent): void {
    // Cmd-K (macOS) / Ctrl-K (everywhere else) opens the palette.
    if ((event.metaKey || event.ctrlKey) && (event.key === 'k' || event.key === 'K')) {
      event.preventDefault();
      this.openCommandPalette();
    }
  }
}
