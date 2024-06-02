import { Injectable } from '@angular/core';
import { LocationService } from './location.service';
import { Util } from '../util/util';

@Injectable({
  providedIn: 'root',
})
export class WindService {
  private windDirection: number = 0;
  public angleOfAttack: number = 45;

  constructor(private locationService: LocationService) {
    const self = this;
    locationService.subscribeForLocation((location: GeolocationPosition) => {});
  }

  setPortTack() {
    this.windDirection = Util.normaliseDegrees(
      this.locationService.heading - this.angleOfAttack
    );
  }

  setStarboardTack() {
    this.windDirection = Util.normaliseDegrees(
      this.locationService.heading + this.angleOfAttack
    );
  }

  setWindDirection(windDirection: number) {
    this.windDirection = windDirection;
  }

  getWindDirection() {
    return this.windDirection;
  }

  getCalculatedWindDirection() {
    const angleToTheWind = Util.normaliseDegrees(
      this.locationService.heading - this.windDirection
    );

    if (angleToTheWind > 180) {
      return Util.normaliseDegrees(
        this.locationService.heading + this.angleOfAttack
      );
    }
    return Util.normaliseDegrees(
      this.locationService.heading - this.angleOfAttack
    );
  }
}
