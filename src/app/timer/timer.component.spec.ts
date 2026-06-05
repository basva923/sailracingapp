import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TimerComponent } from './timer.component';
import { TimerService } from '../services/timer.service';
import { StartlineService } from '../services/startline.service';

describe('TimerComponent', () => {
  let component: TimerComponent;
  let fixture: ComponentFixture<TimerComponent>;
  let timerService: jasmine.SpyObj<TimerService> & { milliSecondsLeft: number | null };
  let startLineService: Partial<StartlineService>;

  beforeEach(async () => {
    jasmine.clock().install();
    jasmine.clock().mockDate(new Date(2025, 0, 1, 9, 5, 0));
    timerService = jasmine.createSpyObj<TimerService>('TimerService', [
      'startInMinutes',
      'syncToClosestMinute',
      'reset',
    ]) as jasmine.SpyObj<TimerService> & { milliSecondsLeft: number | null };
    timerService.milliSecondsLeft = null;
    startLineService = {
      pinEndSet: false,
      boadEndSet: false,
      startLineLength: null,
      distanceToLine: null,
      timeToLine: null,
      vmgBoatSpeed: 1.5,
      setPinEndPosition: jasmine.createSpy('setPinEndPosition'),
      clearPinEndPosition: jasmine.createSpy('clearPinEndPosition'),
      setBoadEndPosition: jasmine.createSpy('setBoadEndPosition'),
      clearBoadEndPosition: jasmine.createSpy('clearBoadEndPosition'),
    };

    await TestBed.configureTestingModule({
      imports: [TimerComponent],
      providers: [
        { provide: TimerService, useValue: timerService },
        { provide: StartlineService, useValue: startLineService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TimerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    jasmine.clock().uninstall();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should show the current time when no timer is running', () => {
    expect(component.timeLeft).toBe('9:5');
  });

  it('should start timers through the timer service and format time left', () => {
    timerService.milliSecondsLeft = 61000;

    component.startInMinutes(1);

    expect(timerService.startInMinutes).toHaveBeenCalledWith(1);
    expect(component.timeLeft).toBe('01:01');
  });

  it('should calculate start line display values', () => {
    Object.defineProperties(startLineService, {
      startLineLength: { get: () => 565, configurable: true },
      distanceToLine: { get: () => 230, configurable: true },
      timeToLine: { get: () => 100, configurable: true },
    });
    timerService.milliSecondsLeft = 150000;

    component.calcDistanceBetweenBooys();
    component.calcDistanceToLine();
    component.calcTimeToKill();

    expect(component.distanceBetweenBooys).toBe('565m');
    expect(component.distanceToLine).toBe('230m');
    expect(component.timeToKill).toBe('50s');
  });
});
