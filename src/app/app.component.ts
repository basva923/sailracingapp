import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent {
  title = 'sailracingapp';

  constructor() {}

  ngAfterViewInit() {
    this.requestAlwaysOn();
  }

  async requestAlwaysOn() {
    try {
      const wakeLock = await navigator.wakeLock.request('screen');
    } catch (err: any) {
      // the wake lock request fails - usually system related, such being low on battery
      console.log(`${err.name}, ${err.message}`);
    }
  }
}
