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
  private readonly HISTORY_SIZE = 60 * 60 * 8;
  private windDirectionFrequency: number[] = [];

  constructor(private locationService: LocationService) {
    const self = this;
    this.resetWindDirectionFrequency();
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
    this.windDirectionFrequency[
      this.scaleWind(this.getCalculatedWindDirection())
    ] += 1;
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

  getRelativeCalculatedWindDirection() {
    return this.getCalculatedWindDirection() - this.windDirection;
  }

  getWindDirectionHistory(): number[] {
    return this.windDirectionHistory.slice(-1000);
  }

  getRelativeWindDirectionHistory(): number[] {
    return this.getWindDirectionHistory().map((v) => {
      return Util.normaliseDegrees(v - this.windDirection);
    });
  }

  getWindDirectionFrequency() {
    return this.windDirectionFrequency;
  }

  getWindDirectionFrequencyPart() {
    const result = [];
    for (let i = this.windDirection - 40; i < this.windDirection + 40; i++) {
      let j = i;

      if (i < 0) {
        j = i + this.windDirectionFrequency.length;
      } else if (i >= this.windDirectionFrequency.length) {
        j = i - this.windDirectionFrequency.length;
      }
      result.push({
        x: i - this.windDirection,
        y: this.windDirectionFrequency[j],
      });
    }
    return result;
  }

  resetWindDirectionFrequency() {
    this.windDirectionFrequency = [];
    for (let i = 0; i < this.scaleWind(360); i++) {
      this.windDirectionFrequency.push(0);
    }
  }

  private scaleWind(d: number) {
    return Math.floor(d);
  }
}
