import { Injectable } from '@angular/core';
import { LocationService } from './location.service';
import { Util } from '../util/util';

@Injectable({
  providedIn: 'root',
})
export class WindService {
  private windDirection: number = 0;
  public angleOfAttack: number = 45;
  private windDirectionHistory: number[] = [];
  private readonly HISTORY_SIZE = 30000;

  constructor(private locationService: LocationService) {
    const self = this;
    locationService.subscribeForLocation((location: GeolocationPosition) => {});
    setInterval(() => self.logWind(), 1000);
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

  logWind() {
    this.windDirectionHistory.push(this.getCalculatedWindDirection());
    this.windDirectionHistory = this.windDirectionHistory.slice(
      -this.HISTORY_SIZE,
      this.windDirectionHistory.length
    );
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

  getWindDirectionHistory() {
    return this.windDirectionHistory.slice(-1000);
  }
}
