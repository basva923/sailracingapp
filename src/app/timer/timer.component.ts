import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { Util } from '../util/util';
import { UnitToString } from '../util/unit-to-string';

@Component({
  selector: 'app-timer',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './timer.component.html',
  styleUrl: './timer.component.css',
})
export class TimerComponent {
  startTime: Date | null = null;
  timeLeft: string = '---';
  distanceToLine: string = '---';
  distanceBetweenBooys: string = 'please set the booys';
  pinEndPosition: GeolocationCoordinates | null = null;
  boadEndPosition: GeolocationCoordinates | null = null;
  lastPosition: GeolocationCoordinates | null = null;

  constructor() {
    const self = this;
    setInterval(() => {
      self.calcTimeLeft();
      self.calcDistanceToLine();
    }, 500);

    // start gps watch
    navigator.geolocation.watchPosition(
      (position) => {
        self.lastPosition = position.coords;
        self.calcDistanceToLine();
      },
      (error) => {
        console.error(error);
      },
      { enableHighAccuracy: true }
    );
  }

  startInMinutes(minutes: number) {
    this.startTime = new Date();
    this.startTime.setMinutes(this.startTime.getMinutes() + minutes);
    this.calcTimeLeft();
  }

  syncToClosestMinute() {
    const now = new Date();
    if (this.startTime) {
      const secondsOfMinute = Math.floor(
        (this.timediffInMilliseconds() % 60000) / 1000
      );
      if (secondsOfMinute >= 30) {
        this.startTime.setSeconds(
          this.startTime.getSeconds() + 60 - secondsOfMinute
        );
      } else {
        this.startTime.setSeconds(
          this.startTime.getSeconds() - secondsOfMinute
        );
      }
    }
    this.calcTimeLeft();
  }

  reset() {
    this.startTime = null;
    this.calcTimeLeft();
  }

  calcTimeLeft() {
    if (this.startTime) {
      const diff = this.timediffInMilliseconds();
      this.timeLeft = UnitToString.milisecondsToTime(diff);
    } else {
      // return current time
      const now = new Date();
      this.timeLeft = `${now.getHours()}:${now.getMinutes()}`;
    }
  }

  private timediffInMilliseconds(): number {
    if (this.startTime) {
      const now = new Date();
      const diff = Math.abs(this.startTime.getTime() - now.getTime());
      return diff;
    }
    return 0;
  }

  togglePinEndPositionToCurrentLocation() {
    if (this.pinEndPosition) {
      this.pinEndPosition = null;
    } else {
      this.pinEndPosition = this.lastPosition;
    }
    this.calcDistanceBetweenBooys();
    this.calcDistanceToLine();
  }

  toggleBoadEndPositionToCurrentLocation() {
    if (this.boadEndPosition) {
      this.boadEndPosition = null;
    } else {
      this.boadEndPosition = this.lastPosition;
    }

    this.calcDistanceBetweenBooys();
    this.calcDistanceToLine();
  }

  calcDistanceToLine() {
    if (this.pinEndPosition && this.boadEndPosition && this.lastPosition) {
      this.distanceToLine = UnitToString.metersToString(
        Util.distancePointToLine(
          this.lastPosition.latitude,
          this.lastPosition.longitude,
          this.pinEndPosition.latitude,
          this.pinEndPosition.longitude,
          this.boadEndPosition.latitude,
          this.boadEndPosition.longitude
        )
      );
    } else {
      this.distanceToLine = '---';
    }
  }

  calcDistanceBetweenBooys() {
    if (this.pinEndPosition && this.boadEndPosition) {
      this.distanceBetweenBooys = UnitToString.metersToString(
        Util.haversineDistanceBetweenPoints(
          this.pinEndPosition.latitude,
          this.pinEndPosition.longitude,
          this.boadEndPosition.latitude,
          this.boadEndPosition.longitude
        )
      );
    } else {
      this.distanceBetweenBooys = 'please set the booys';
    }
  }
}
