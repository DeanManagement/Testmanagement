import { Component } from '@angular/core';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule } from '@ngx-translate/core';

/**
 * Cheatsheet shown when the user presses `?` on a test run detail page.
 * Lists the keyboard bindings that drive test execution.
 */
@Component({
  selector: 'app-keyboard-shortcuts-dialog',
  standalone: true,
  imports: [MatDialogModule, MatButtonModule, MatIconModule, TranslateModule],
  template: `
    <h2 mat-dialog-title>{{ 'testRun.shortcuts.title' | translate }}</h2>
    <mat-dialog-content>
      <table class="shortcut-table">
        <tbody>
          @for (row of bindings; track row.keys) {
            <tr>
              <td class="keys">
                @for (k of row.keys; track k; let last = $last) {
                  <kbd>{{ k }}</kbd>@if (!last) {<span class="sep">/</span>}
                }
              </td>
              <td class="description">{{ row.descriptionKey | translate }}</td>
            </tr>
          }
        </tbody>
      </table>
      <p class="hint">{{ 'testRun.shortcuts.hint' | translate }}</p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="close()">{{ 'common.close' | translate }}</button>
    </mat-dialog-actions>
  `,
  styles: [`
    .shortcut-table {
      width: 100%;
      border-collapse: collapse;
    }
    .shortcut-table td {
      padding: 6px 8px;
      vertical-align: middle;
    }
    .keys {
      white-space: nowrap;
      width: 1%;
    }
    kbd {
      display: inline-block;
      padding: 2px 6px;
      font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
      font-size: 0.85em;
      border: 1px solid rgba(0, 0, 0, 0.2);
      border-bottom-width: 2px;
      border-radius: 4px;
      background: rgba(0, 0, 0, 0.04);
    }
    .sep {
      margin: 0 4px;
      opacity: 0.5;
    }
    .description {
      padding-left: 12px;
    }
    .hint {
      margin-top: 16px;
      opacity: 0.7;
      font-size: 0.9em;
    }
  `],
})
export class KeyboardShortcutsDialogComponent {
  bindings = [
    { keys: ['J', '↓'], descriptionKey: 'testRun.shortcuts.nextResult' },
    { keys: ['K', '↑'], descriptionKey: 'testRun.shortcuts.prevResult' },
    { keys: ['P'], descriptionKey: 'testRun.shortcuts.markPassed' },
    { keys: ['F'], descriptionKey: 'testRun.shortcuts.markFailed' },
    { keys: ['B'], descriptionKey: 'testRun.shortcuts.markBlocked' },
    { keys: ['S'], descriptionKey: 'testRun.shortcuts.markSkipped' },
    { keys: ['Shift+P'], descriptionKey: 'testRun.shortcuts.allStepsPassed' },
    { keys: ['C'], descriptionKey: 'testRun.shortcuts.focusComment' },
    { keys: ['?'], descriptionKey: 'testRun.shortcuts.showHelp' },
  ];

  constructor(private readonly dialogRef: MatDialogRef<KeyboardShortcutsDialogComponent>) {}

  close(): void {
    this.dialogRef.close();
  }
}
