import { CommonModule } from '@angular/common';
import { Component, OnDestroy } from '@angular/core';
import { Util } from '../util/util';
import { UnitToString } from '../util/unit-to-string';

@Component({
  selector: 'app-timer',
  imports: [CommonModule],
  templateUrl: './timer.component.html',
  styleUrl: './timer.component.css',
})
export class TimerComponent implements OnDestroy {
  startTime: Date | null = null;
  timeLeft: string = '---';
  distanceToLine: string = '---';
  distanceBetweenBooys: string = 'please set the booys';
  pinEndPosition: GeolocationCoordinates | null = null;
  boadEndPosition: GeolocationCoordinates | null = null;
  lastPosition: GeolocationCoordinates | null = null;

  private intervalId: ReturnType<typeof setInterval> | null = null;
  private watchId: number | null = null;
  private readonly handleVisibilityChange = () => this.onVisibilityChange();

  constructor() {
    this.start();
    document.addEventListener('visibilitychange', this.handleVisibilityChange);
  }

  ngOnDestroy() {
    this.stop();
    document.removeEventListener(
      'visibilitychange',
      this.handleVisibilityChange
    );
  }

  private onVisibilityChange() {
    // Pause sensors and timers while the app is in the background to save
    // battery, and resume them when it becomes visible again.
    if (document.hidden) {
      this.stop();
    } else {
      this.start();
    }
  }

  private start() {
    if (this.intervalId === null) {
      // A one second tick is enough for the displayed countdown; the remaining
      // time is recomputed from startTime so accuracy does not depend on a
      // high tick rate.
      this.intervalId = setInterval(() => {
        this.calcTimeLeft();
        this.calcDistanceToLine();
      }, 1000);
    }

    if (this.watchId === null) {
      this.watchId = navigator.geolocation.watchPosition(
        (position) => {
          this.lastPosition = position.coords;
          this.calcDistanceToLine();
        },
        (error) => {
          console.error(error);
        },
        { enableHighAccuracy: this.needsHighAccuracy() }
      );
    }
  }

  private stop() {
    if (this.intervalId !== null) {
      clearInterval(this.intervalId);
      this.intervalId = null;
    }
    if (this.watchId !== null) {
      navigator.geolocation.clearWatch(this.watchId);
      this.watchId = null;
    }
  }

  private needsHighAccuracy(): boolean {
    // High accuracy GPS is only required once the start line is being set up,
    // when distance-to-line is actually shown. Otherwise let the OS use the
    // lower power location provider.
    return this.pinEndPosition !== null || this.boadEndPosition !== null;
  }

  private restartGpsWatch() {
    // Re-acquire the GPS watch so a change in required accuracy takes effect.
    if (this.watchId !== null) {
      navigator.geolocation.clearWatch(this.watchId);
      this.watchId = null;
    }
    if (!document.hidden) {
      this.start();
    }
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
    this.restartGpsWatch();
    this.calcDistanceBetweenBooys();
    this.calcDistanceToLine();
  }

  toggleBoadEndPositionToCurrentLocation() {
    if (this.boadEndPosition) {
      this.boadEndPosition = null;
    } else {
      this.boadEndPosition = this.lastPosition;
    }

    this.restartGpsWatch();
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
