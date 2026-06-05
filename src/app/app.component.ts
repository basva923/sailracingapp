import { Component, ChangeDetectionStrategy, OnDestroy } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './app.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './app.component.css',
})
export class AppComponent implements OnDestroy {
  title = 'sailracingapp';
  private wakeLock?: WakeLockSentinel;
  private readonly visibilityChangeHandler = () => this.handleVisibilityChange();

  constructor() {
    document.addEventListener('visibilitychange', this.visibilityChangeHandler);
  }

  ngAfterViewInit() {
    this.requestAlwaysOn();
  }

  ngOnDestroy() {
    document.removeEventListener('visibilitychange', this.visibilityChangeHandler);
    this.wakeLock?.release();
    this.wakeLock = undefined;
  }

  private handleVisibilityChange() {
    if (!document.hidden) {
      this.requestAlwaysOn();
    }
  }

  async requestAlwaysOn() {
    if (!navigator.wakeLock || this.wakeLock) {
      return;
    }

    try {
      this.wakeLock = await navigator.wakeLock.request('screen');
      this.wakeLock.addEventListener('release', () => {
        this.wakeLock = undefined;
      });
    } catch (err: any) {
      // the wake lock request fails - usually system related, such being low on battery
      console.log(`${err.name}, ${err.message}`);
    }
  }
}
