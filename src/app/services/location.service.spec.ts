import { TestBed } from '@angular/core/testing';

import { LocationService } from './location.service';

describe('LocationService', () => {
  let service: LocationService;
  let successCallback: PositionCallback;
  let watchPositionSpy: jasmine.Spy;
  let clearWatchSpy: jasmine.Spy;

  function position(
    latitude: number,
    longitude: number,
    speed: number | null,
    timestamp: number
  ): GeolocationPosition {
    return {
      coords: {
        latitude,
        longitude,
        altitude: null,
        accuracy: 5,
        altitudeAccuracy: null,
        heading: null,
        speed,
      },
      timestamp,
    } as GeolocationPosition;
  }

  beforeEach(() => {
    successCallback = undefined as unknown as PositionCallback;
    watchPositionSpy = spyOn(navigator.geolocation, 'watchPosition').and.callFake(
      (success: PositionCallback) => {
        successCallback = success;
        return 42;
      }
    );
    clearWatchSpy = spyOn(navigator.geolocation, 'clearWatch');

    TestBed.configureTestingModule({});
    service = TestBed.inject(LocationService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
    expect(watchPositionSpy).toHaveBeenCalledWith(
      jasmine.any(Function),
      jasmine.any(Function),
      { enableHighAccuracy: true }
    );
  });

  it('should expose current coordinates and location getters from watched positions', () => {
    successCallback(position(51, 5, 0, 1000));
    successCallback(position(52, 6, 4, 2000));
    successCallback(position(53, 7, 8, 3000));

    expect(service.curCoordinates?.latitude).toBe(53);
    expect(service.curLatitude).toBe(53);
    expect(service.curLongitude).toBe(7);
    expect(service.curSpeed).toBe(8);
    expect(service.curTimestamp).toBe(3000);
    expect(service.maxSpeed).toBe(8);
    expect(service.avgSpeed).toBeCloseTo(4, 6);
    expect(service.coordinatesLog.map((coords) => coords.latitude)).toEqual([51, 52, 53]);
  });

  it('should return null current values before receiving a position', () => {
    expect(service.curCoordinates).toBeNull();
    expect(service.curLatitude).toBeNull();
    expect(service.curLongitude).toBeNull();
    expect(service.curSpeed).toBeNull();
    expect(service.curTimestamp).toBeNull();
  });

  it('should publish watched positions to subscribers and unsubscribe cleanly', () => {
    const callback = jasmine.createSpy('callback');
    const unsubscribe = service.subscribeForLocation(callback);
    const first = position(51, 5, 1, 1000);
    const second = position(52, 6, 2, 2000);

    successCallback(first);
    unsubscribe();
    successCallback(second);

    expect(callback).toHaveBeenCalledOnceWith(first);
  });

  it('should track phone pointing direction', () => {
    expect(service.phoneIsPointingForward).toBeTrue();

    service.phoneIsPointingForward = false;
    expect(service.phoneIsPointingForward).toBeFalse();

    service.phoneIsPointingForward = true;
    expect(service.phoneIsPointingForward).toBeTrue();
  });

  it('should calculate a numeric heading after orientation changes', () => {
    service.handleOrientationChange({
      absolute: true,
      alpha: 90,
      beta: 30,
      gamma: 10,
    } as DeviceOrientationEvent);

    expect(service.heading).toEqual(jasmine.any(Number));
    expect(Number.isNaN(service.heading)).toBeFalse();
  });

  it('should clear watch when the page becomes hidden', () => {
    spyOnProperty(document, 'hidden', 'get').and.returnValue(true);

    document.dispatchEvent(new Event('visibilitychange'));

    expect(clearWatchSpy).toHaveBeenCalledWith(42);
  });
});
