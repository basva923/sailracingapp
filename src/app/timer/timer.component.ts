
import { Component, ChangeDetectionStrategy } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatGridListModule } from '@angular/material/grid-list';
import { Util } from '../util/util';
import { UnitToString } from '../util/unit-to-string';
import { TimerService } from '../services/timer.service';
import { StartlineService } from '../services/startline.service';

@Component({
  selector: 'app-timer',
  standalone: true,
  imports: [
    MatCardModule,
    MatButtonModule,
    MatDividerModule,
    MatGridListModule
],
  templateUrl: './timer.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './timer.component.css',
})
export class TimerComponent {
  timeLeft: string = '---';
  distanceToLine: string = '---';
  timeToKill: string = '---';
  distanceBetweenBooys: string = 'please set the booys';
  lastPosition: GeolocationCoordinates | null = null;

  constructor(
    private timerService: TimerService,
    protected startLineService: StartlineService
  ) {
    const self = this;
    setInterval(() => {
      self.calcTimeLeft();
      self.calcDistanceToLine();
      self.calcTimeToKill();
      self.calcDistanceBetweenBooys();
    }, 500);
  }

  startInMinutes(minutes: number) {
    this.timerService.startInMinutes(minutes);
    this.calcTimeLeft();
  }

  syncToClosestMinute() {
    this.timerService.syncToClosestMinute();
    this.calcTimeLeft();
  }

  reset() {
    if (confirm('Are you sure to stop the timer?')) {
      this.timerService.reset();
      this.calcTimeLeft();
    }
  }

  calcTimeLeft() {
    const diff = this.timerService.milliSecondsLeft;
    if (diff) {
      this.timeLeft = UnitToString.milisecondsToTime(diff);
    } else {
      // return current time
      const now = new Date();
      this.timeLeft = `${now.getHours()}:${now.getMinutes()}`;
    }
  }

  togglePinEndPositionToCurrentLocation() {
    if (this.startLineService.pinEndSet) {
      this.startLineService.clearPinEndPosition();
    } else {
      this.startLineService.setPinEndPosition();
    }
    this.calcDistanceBetweenBooys();
    this.calcDistanceToLine();
  }

  toggleBoadEndPositionToCurrentLocation() {
    if (this.startLineService.boadEndSet) {
      this.startLineService.clearBoadEndPosition();
    } else {
      this.startLineService.setBoadEndPosition();
    }
    this.calcDistanceBetweenBooys();
    this.calcDistanceToLine();
  }

  calcDistanceToLine() {
    if (this.startLineService.distanceToLine) {
      this.distanceToLine = UnitToString.metersToString(
        this.startLineService.distanceToLine
      );
    } else {
      this.distanceToLine = '---';
    }
  }

  calcTimeToKill() {
    if (
      this.startLineService.timeToLine &&
      this.timerService.milliSecondsLeft
    ) {
      this.timeToKill = UnitToString.secondsToString(
        this.timerService.milliSecondsLeft / 1000 -
        this.startLineService.timeToLine
      );
    } else {
      this.timeToKill = '---';
    }
  }

  calcDistanceBetweenBooys() {
    if (this.startLineService.startLineLength) {
      this.distanceBetweenBooys = UnitToString.metersToString(
        this.startLineService.startLineLength
      );
    } else {
      this.distanceBetweenBooys = 'please set the booys';
    }
  }

  handleVMGUpdate() {
    const currentVMG = this.startLineService.vmgBoatSpeed / 0.514444444;
    const vmgKnots = Number(
      prompt('Enter you VMG in knots.', currentVMG.toFixed(2))
    );
    this.startLineService.vmgBoatSpeed = vmgKnots * 0.514444444;
  }
}
