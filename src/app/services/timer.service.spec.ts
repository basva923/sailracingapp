import { TestBed } from '@angular/core/testing';

import { TimerService } from './timer.service';

describe('TimerService', () => {
  let service: TimerService;
  const now = new Date(2025, 0, 1, 12, 0, 0, 0);

  beforeEach(() => {
    jasmine.clock().install();
    jasmine.clock().mockDate(now);
    TestBed.configureTestingModule({});
    service = TestBed.inject(TimerService);
  });

  afterEach(() => {
    jasmine.clock().uninstall();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should return null when no start time is configured', () => {
    expect(service.milliSecondsLeft).toBeNull();
  });

  it('should start a timer by minutes', () => {
    service.startInMinutes(4);

    expect(service.milliSecondsLeft).toBe(4 * 60000);
  });

  it('should reset the timer', () => {
    service.startInMinutes(1);
    service.reset();

    expect(service.milliSecondsLeft).toBeNull();
  });

  it('should sync down to the closest minute below thirty seconds', () => {
    service.startTime = new Date(2025, 0, 1, 12, 5, 29, 0);

    service.syncToClosestMinute();

    expect(service.startTime).toEqual(new Date(2025, 0, 1, 12, 5, 0, 0));
    expect(service.milliSecondsLeft).toBe(5 * 60000);
  });

  it('should sync up to the closest minute at or above thirty seconds', () => {
    service.startTime = new Date(2025, 0, 1, 12, 5, 31, 0);

    service.syncToClosestMinute();

    expect(service.startTime).toEqual(new Date(2025, 0, 1, 12, 6, 0, 0));
    expect(service.milliSecondsLeft).toBe(6 * 60000);
  });
});
