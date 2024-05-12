import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';

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
  pinEndPosition: GeolocationCoordinates | null = null;
  boadEndPosition: GeolocationCoordinates | null = null;

  constructor() {
    const self = this;
    setInterval(() => {
      self.calcTimeLeft();
      self.calcDistanceToLine();
    }, 500);
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

      const minutes = Math.floor(diff / 60000);
      const seconds = Math.floor((diff % 60000) / 1000);
      this.timeLeft = `${minutes}:${seconds < 10 ? '0' : ''}${seconds}`;
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
      this.calcDistanceToLine();
    } else {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          this.pinEndPosition = position.coords;
          this.calcDistanceToLine();
        },
        (error) => {},
        { enableHighAccuracy: true }
      );
    }
  }

  toggleBoadEndPositionToCurrentLocation() {
    if (this.boadEndPosition) {
      this.boadEndPosition = null;
      this.calcDistanceToLine();
    } else {
      navigator.geolocation.getCurrentPosition(
        (position) => {
          this.boadEndPosition = position.coords;
          this.calcDistanceToLine();
        },
        (error) => {},
        { enableHighAccuracy: true }
      );
    }
  }

  calcDistanceToLine() {
    const self = this;
    navigator.geolocation.getCurrentPosition(
      (position) => {
        if (self.pinEndPosition && self.boadEndPosition) {
          self.distanceToLine =
            this.distancePointToLine(
              position.coords.latitude,
              position.coords.longitude,
              self.pinEndPosition.latitude,
              self.pinEndPosition.longitude,
              self.boadEndPosition.latitude,
              self.boadEndPosition.longitude
            ).toFixed(0) + 'm';
        } else {
          this.distanceToLine = '---';
        }
      },
      (error) => {},
      { enableHighAccuracy: true }
    );
  }

  distancePointToLine(
    x0: number,
    y0: number,
    x1: number,
    y1: number,
    x2: number,
    y2: number
  ) {
    return (
      Math.abs((x2 - x1) * (y1 - y0) - (x1 - x0) * (y2 - y1)) /
      Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2))
    );
  }
}
