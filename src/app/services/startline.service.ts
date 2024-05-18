import { Injectable } from '@angular/core';
import { LocationService } from './location.service';
import { Util } from '../util/util';

@Injectable({
  providedIn: 'root',
})
export class StartlineService {
  pinEndPosition: GeolocationCoordinates | null = null;
  boadEndPosition: GeolocationCoordinates | null = null;

  constructor(private locationService: LocationService) {}

  setPinEndPosition() {
    this.pinEndPosition = this.locationService.curCoordinates;
  }

  setBoadEndPosition() {
    this.boadEndPosition = this.locationService.curCoordinates;
  }

  clearPinEndPosition() {
    this.pinEndPosition = null;
  }

  clearBoadEndPosition() {
    this.boadEndPosition = null;
  }

  get pinEndSet(): boolean {
    return this.pinEndPosition !== null;
  }

  get boadEndSet(): boolean {
    return this.boadEndPosition !== null;
  }

  get startLineLength(): number | null {
    if (!this.pinEndPosition || !this.boadEndPosition) {
      return null;
    }
    return Util.haversineDistanceBetweenPoints(
      this.pinEndPosition.latitude,
      this.pinEndPosition.longitude,
      this.boadEndPosition.latitude,
      this.boadEndPosition.longitude
    );
  }

  get distanceToLine(): number | null {
    if (
      !this.pinEndPosition ||
      !this.boadEndPosition ||
      !this.locationService.curLatitude ||
      !this.locationService.curLongitude
    ) {
      return null;
    }
    return Util.distancePointToLine(
      this.locationService.curLatitude,
      this.locationService.curLongitude,
      this.pinEndPosition.latitude,
      this.pinEndPosition.longitude,
      this.boadEndPosition.latitude,
      this.boadEndPosition.longitude
    );
  }
}
