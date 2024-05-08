import { Component } from '@angular/core';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
})
export class HomeComponent {
  absolute = false;
  alpha: number = 0;
  beta: number = 0;
  gamma: number = 0;

  constructor() {
    window.addEventListener(
      'deviceorientation',
      (e) => this.handleOrientationChange(e),
      true
    );
  }

  handleOrientationChange(event: DeviceOrientationEvent) {
    this.absolute = event.absolute;
    if (event.alpha != null) this.alpha = Math.round(event.alpha);
    if (event.beta != null) this.beta = Math.round(event.beta);
    if (event.gamma != null) this.gamma = Math.round(event.gamma);
  }

  get heading() {
    return (360 - this.alpha).toFixed(0);
  }
}
