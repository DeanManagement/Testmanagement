import { Component, DestroyRef, inject, Input, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Store } from '@ngrx/store';
import { AsyncPipe } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { Observable, of } from 'rxjs';
import { take } from 'rxjs/operators';
import { WatchableEntityType } from '../../models/watcher.model';
import { WatcherActions } from '../../../store/watcher/watcher.actions';
import { selectIsWatching } from '../../../store/watcher/watcher.selectors';

@Component({
  selector: 'app-watch-toggle',
  standalone: true,
  imports: [
    AsyncPipe,
    MatButtonModule,
    MatIconModule,
    MatTooltipModule,
    TranslateModule,
  ],
  template: `
    @if (isWatching$ | async) {
      <button mat-icon-button
        (click)="toggleWatch()"
        [matTooltip]="'watcher.unwatch' | translate"
        data-test-id="watch-toggle-btn">
        <mat-icon>bookmark</mat-icon>
      </button>
    } @else {
      <button mat-icon-button
        (click)="toggleWatch()"
        [matTooltip]="'watcher.watch' | translate"
        data-test-id="watch-toggle-btn">
        <mat-icon>bookmark_border</mat-icon>
      </button>
    }
  `,
})
export class WatchToggleComponent implements OnInit {
  private readonly store = inject(Store);
  private readonly destroyRef = inject(DestroyRef);

  @Input({ required: true }) entityType!: WatchableEntityType;
  @Input({ required: true }) entityId!: string;

  isWatching$: Observable<boolean> = of(false);

  ngOnInit(): void {
    this.store.dispatch(WatcherActions.checkWatchStatus({
      entityType: this.entityType,
      entityId: this.entityId,
    }));
    this.isWatching$ = this.store.select(selectIsWatching(this.entityType, this.entityId));
  }

  toggleWatch(): void {
    // Called from the template (not an injection context) — pass the
    // DestroyRef explicitly.
    this.isWatching$.pipe(take(1), takeUntilDestroyed(this.destroyRef)).subscribe((watching) => {
      if (watching) {
        this.store.dispatch(WatcherActions.unwatchEntity({
          entityType: this.entityType,
          entityId: this.entityId,
        }));
      } else {
        this.store.dispatch(WatcherActions.watchEntity({
          entityType: this.entityType,
          entityId: this.entityId,
        }));
      }
    });
  }
}
