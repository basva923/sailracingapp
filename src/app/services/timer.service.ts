import { Injectable } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class TimerService {
  startTime: Date | null = null;

  constructor() {}

  startInMinutes(minutes: number) {
    this.startTime = new Date();
    this.startTime.setMinutes(this.startTime.getMinutes() + minutes);
  }

  syncToClosestMinute() {
    const now = new Date();
    if (this.startTime) {
      const secondsOfMinute = Math.floor(
        (this.milliSecondsLeft! % 60000) / 1000
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
  }

  reset() {
    this.startTime = null;
  }

  get milliSecondsLeft(): number|null {
    if (this.startTime) {
      const now = new Date();
      const diff = Math.abs(this.startTime.getTime() - now.getTime());
      return diff;
    }
    return null;
  }
}
