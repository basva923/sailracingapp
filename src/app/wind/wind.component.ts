import { Component, OnDestroy } from '@angular/core';

@Component({
  selector: 'app-wind',
  imports: [],
  templateUrl: './wind.component.html',
  styleUrl: './wind.component.css',
})
export class WindComponent implements OnDestroy {
  absolute = false;
  alpha: number = 0;
  beta: number = 0;
  gamma: number = 0;

  // Cached heading so the trigonometry is only recomputed when the orientation
  // actually changes, instead of on every change-detection cycle.
  private cachedHeading = 0;
  private headingDirty = true;

  // Throttle orientation updates: a few updates per second is plenty for a
  // compass and avoids waking the CPU on every (up to 60+ Hz) sensor event.
  private static readonly UPDATE_INTERVAL_MS = 200;
  private lastUpdate = 0;

  private readonly handleOrientation = (e: DeviceOrientationEvent) =>
    this.handleOrientationChange(e);
  private readonly handleVisibilityChange = () => this.onVisibilityChange();

  constructor() {
    this.startListening();
    document.addEventListener('visibilitychange', this.handleVisibilityChange);
  }

  ngOnDestroy() {
    this.stopListening();
    document.removeEventListener(
      'visibilitychange',
      this.handleVisibilityChange
    );
  }

  private onVisibilityChange() {
    // Stop reading the orientation sensor while the app is backgrounded.
    if (document.hidden) {
      this.stopListening();
    } else {
      this.startListening();
    }
  }

  private startListening() {
    window.addEventListener(
      'deviceorientationabsolute',
      this.handleOrientation,
      true
    );
  }

  private stopListening() {
    window.removeEventListener(
      'deviceorientationabsolute',
      this.handleOrientation,
      true
    );
  }

  handleOrientationChange(event: DeviceOrientationEvent) {
    const now = Date.now();
    if (now - this.lastUpdate < WindComponent.UPDATE_INTERVAL_MS) {
      return;
    }
    this.lastUpdate = now;

    this.absolute = event.absolute;
    if (event.alpha != null) this.alpha = event.alpha;
    if (event.beta != null) this.beta = event.beta;
    if (event.gamma != null) this.gamma = event.gamma;
    this.headingDirty = true;
  }

  get heading() {
    if (!this.headingDirty) {
      return this.cachedHeading;
    }
    this.headingDirty = false;

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
    this.cachedHeading = compassHeading;
    return compassHeading;
  }
}
