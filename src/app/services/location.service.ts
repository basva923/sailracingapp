import { Injectable } from '@angular/core';

export class LocationEvent extends Event {
  location: GeolocationPosition;
  constructor(location: GeolocationPosition) {
    super('newLocation');
    this.location = location;
  }
}

@Injectable({
  providedIn: 'root',
})
export class LocationService {
  private locations: GeolocationPosition[] = [];
  public currentLocationEvent = new EventTarget();
  private absolute = false;
  private alpha: number = 0;
  private beta: number = 0;
  private gamma: number = 0;
  private watchId: number | null = null;
  private readonly visibilityChangeHandler = () => this.handleVisibilityChange();

  private reversedPhone = false;

  constructor() {
    this.startWatch();

    window.addEventListener(
      'deviceorientationabsolute',
      (e) => this.handleOrientationChange(e),
      true
    );
    document.addEventListener('visibilitychange', this.visibilityChangeHandler);
  }

  private startWatch() {
    if (document.hidden || this.watchId !== null) {
      return;
    }

    this.watchId = navigator.geolocation.watchPosition(
      (position) => {
        this.locations.push(position);
        this.currentLocationEvent.dispatchEvent(new LocationEvent(position));
      },
      (error) => {
        console.error(error);
      },
      { enableHighAccuracy: true }
    );
  }

  private stopWatch() {
    if (this.watchId === null) {
      return;
    }

    navigator.geolocation.clearWatch(this.watchId);
    this.watchId = null;
  }

  private handleVisibilityChange() {
    if (document.hidden) {
      this.stopWatch();
    } else {
      this.startWatch();
    }
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

    if (this.reversedPhone) {
      compassHeading += compassHeading >= Math.PI ? -Math.PI : Math.PI;
    }
    // Convert radians to degrees
    compassHeading *= 180 / Math.PI;
    return compassHeading;
  }

  public subscribeForLocation(
    callback: (location: GeolocationPosition) => void
  ) {
    const handler = (event: Event) => {
      callback((event as LocationEvent).location);
    };
    this.currentLocationEvent.addEventListener('newLocation', handler);
    return () => this.currentLocationEvent.removeEventListener('newLocation', handler);
  }

  set phoneIsPointingForward(pointingForward: boolean) {
    this.reversedPhone = !pointingForward;
  }

  get phoneIsPointingForward() {
    return !this.reversedPhone;
  }

  get curCoordinates(): GeolocationCoordinates | null {
    return this.locations[this.locations.length - 1]?.coords || null;
  }

  get curLatitude(): number | null {
    return this.curCoordinates?.latitude || null;
  }

  get curLongitude(): number | null {
    return this.curCoordinates?.longitude || null;
  }

  get curAltitude(): number | null {
    return this.curCoordinates?.altitude || null;
  }

  get curAccuracy(): number | null {
    return this.curCoordinates?.accuracy || null;
  }

  get curAltitudeAccuracy(): number | null {
    return this.curCoordinates?.altitudeAccuracy || null;
  }

  get curHeading(): number | null {
    return this.curCoordinates?.heading || null;
  }

  get curSpeed(): number | null {
    return this.curCoordinates?.speed || null;
  }

  get curTimestamp(): number | null {
    return this.locations[this.locations.length - 1]?.timestamp || null;
  }

  get maxSpeed(): number | null {
    let maxSpeed = 0;
    for (let i = 1; i < this.locations.length; i++) {
      const speed = this.locations[i].coords.speed || 0;
      if (speed > maxSpeed) {
        maxSpeed = speed;
      }
    }
    return maxSpeed;
  }

  get avgSpeed(): number {
    if (this.locations.length === 0) {
      return 0;
    }
    let sumSpeed = 0;
    for (let i = 1; i < this.locations.length; i++) {
      sumSpeed += this.locations[i].coords.speed || 0;
    }
    return sumSpeed / this.locations.length;
  }

  get coordinatesLog(): GeolocationCoordinates[] {
    return this.locations.map((location) => location.coords);
  }
}
