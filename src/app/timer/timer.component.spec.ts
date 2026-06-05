import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TimerComponent } from './timer.component';

function coords(latitude: number, longitude: number): GeolocationCoordinates {
  return {
    latitude,
    longitude,
    accuracy: 1,
    altitude: null,
    altitudeAccuracy: null,
    heading: null,
    speed: null,
    toJSON() {
      return {};
    },
  } as GeolocationCoordinates;
}

describe('TimerComponent', () => {
  let component: TimerComponent;
  let fixture: ComponentFixture<TimerComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TimerComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(TimerComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should clear the interval and GPS watch on destroy', () => {
    const clearWatch = spyOn(navigator.geolocation, 'clearWatch');
    const clearIntervalSpy = spyOn(window, 'clearInterval').and.callThrough();
    component.ngOnDestroy();
    expect(clearWatch).toHaveBeenCalled();
    expect(clearIntervalSpy).toHaveBeenCalled();
  });

  it('should pause the GPS watch when the page becomes hidden', () => {
    const clearWatch = spyOn(navigator.geolocation, 'clearWatch');
    spyOnProperty(document, 'hidden').and.returnValue(true);
    document.dispatchEvent(new Event('visibilitychange'));
    expect(clearWatch).toHaveBeenCalled();
  });

  describe('countdown', () => {
    it('sets a future start time when starting in minutes', () => {
      const before = Date.now();
      component.startInMinutes(5);
      expect(component.startTime).not.toBeNull();
      const target = component.startTime!.getTime();
      expect(target).toBeGreaterThanOrEqual(before + 5 * 60000 - 1000);
      expect(target).toBeLessThanOrEqual(Date.now() + 5 * 60000 + 1000);
    });

    it('updates the displayed time left after starting', () => {
      component.startInMinutes(5);
      expect(component.timeLeft).not.toBe('---');
    });

    it('shows the current time when there is no start time', () => {
      component.startTime = null;
      component.calcTimeLeft();
      expect(component.timeLeft).toMatch(/^\d{1,2}:\d{1,2}$/);
    });

    it('clears the start time on reset', () => {
      component.startInMinutes(5);
      component.reset();
      expect(component.startTime).toBeNull();
    });

    it('snaps the start time to the closest minute', () => {
      const now = new Date();
      // 40 seconds into the current minute should round up to the next minute.
      component.startTime = new Date(now.getTime() + 2 * 60000 + 40000);
      component.syncToClosestMinute();
      const remainderSeconds =
        Math.floor(
          (Math.abs(component.startTime.getTime() - Date.now()) % 60000) / 1000
        );
      // After snapping, the remaining seconds within the minute is near 0 or 59.
      expect(remainderSeconds === 0 || remainderSeconds === 59).toBeTrue();
    });
  });

  describe('start line measurements', () => {
    beforeEach(() => {
      // Avoid touching the real geolocation API when toggling positions.
      spyOn(navigator.geolocation, 'clearWatch');
      spyOn(navigator.geolocation, 'watchPosition').and.returnValue(1);
    });

    it('pins the current location and clears it on a second toggle', () => {
      component.lastPosition = coords(52.0, 4.0);
      component.togglePinEndPositionToCurrentLocation();
      expect(component.pinEndPosition).toBe(component.lastPosition);

      component.togglePinEndPositionToCurrentLocation();
      expect(component.pinEndPosition).toBeNull();
    });

    it('sets the boat end position from the current location', () => {
      component.lastPosition = coords(52.0, 4.0);
      component.toggleBoadEndPositionToCurrentLocation();
      expect(component.boadEndPosition).toBe(component.lastPosition);
    });

    it('reports the distance between the two start line ends', () => {
      component.pinEndPosition = coords(52.0, 4.0);
      component.boadEndPosition = coords(52.001, 4.0);
      component.calcDistanceBetweenBooys();
      expect(component.distanceBetweenBooys).toMatch(/^\d+\.\d{2}m$/);
      expect(component.distanceBetweenBooys).not.toBe('please set the booys');
    });

    it('falls back to the placeholder when an end is missing', () => {
      component.pinEndPosition = coords(52.0, 4.0);
      component.boadEndPosition = null;
      component.calcDistanceBetweenBooys();
      expect(component.distanceBetweenBooys).toBe('please set the booys');
    });

    it('reports the distance to the start line when fully set up', () => {
      component.pinEndPosition = coords(52.0, 4.0);
      component.boadEndPosition = coords(52.0, 4.001);
      component.lastPosition = coords(52.001, 4.0005);
      component.calcDistanceToLine();
      expect(component.distanceToLine).toMatch(/^\d+\.\d{2}m$/);
    });

    it('shows --- for distance to line when a position is missing', () => {
      component.pinEndPosition = coords(52.0, 4.0);
      component.boadEndPosition = coords(52.0, 4.001);
      component.lastPosition = null;
      component.calcDistanceToLine();
      expect(component.distanceToLine).toBe('---');
    });
  });
});
