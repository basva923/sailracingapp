import { Injectable } from '@angular/core';

class LocationEvent extends Event {
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

  constructor() {
    // start gps watch
    const self = this;
    navigator.geolocation.watchPosition(
      (position) => {
        self.locations.push(position);
        self.currentLocationEvent.dispatchEvent(new LocationEvent(position));
      },
      (error) => {
        console.error(error);
      },
      { enableHighAccuracy: true }
    );
  }

  public subscribeForLocation(
    callback: (location: GeolocationPosition) => void
  ) {
    this.currentLocationEvent.addEventListener('newLocation', (event) => {
      callback((event as LocationEvent).location);
    });
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
