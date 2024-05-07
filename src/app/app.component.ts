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

  protected absolute = false;
  protected alpha: number | null = 0;
  protected beta: number | null = 0;
  protected gamma: number | null = 0;

  constructor() {
    window.addEventListener('deviceorientation', this.handleOrientation, true);
  }

  handleOrientation(event: DeviceOrientationEvent) {
    this.absolute = event.absolute;
    this.alpha = event.alpha;
    this.beta = event.beta;
    this.gamma = event.gamma;

    // Do stuff with the new orientation data
  }
}
