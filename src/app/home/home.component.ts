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
    if (event.alpha != null) this.alpha = event.alpha;
    if (event.beta != null) this.beta = event.beta;
    if (event.gamma != null) this.gamma = event.gamma;
  }

  get heading() {
    // Convert degrees to radians
    var alphaRad = this.alpha * (Math.PI / 180);
    var betaRad = this.beta * (Math.PI / 180);
    var gammaRad = this.gamma * (Math.PI / 180);

    // Calculate equation components
    var cA = Math.cos(alphaRad);
    var sA = Math.sin(alphaRad);
    var cB = Math.cos(betaRad);
    var sB = Math.sin(betaRad);
    var cG = Math.cos(gammaRad);
    var sG = Math.sin(gammaRad);

    // Calculate A, B, C rotation components
    var rA = -cA * sG - sA * sB * cG;
    var rB = -sA * sG + cA * sB * cG;
    var rC = -cB * cG;

    // Calculate compass heading
    var compassHeading = Math.atan(rA / rB);

    // Convert from half unit circle to whole unit circle
    if (rB < 0) {
      compassHeading += Math.PI;
    } else if (rA < 0) {
      compassHeading += 2 * Math.PI;
    }

    // Convert radians to degrees
    compassHeading *= 180 / Math.PI;
    return compassHeading;
  }
}
