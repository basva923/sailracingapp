import { TestBed } from '@angular/core/testing';

import { StartlineService } from './startline.service';
import { LocationService } from './location.service';

function coords(latitude: number, longitude: number): GeolocationCoordinates {
  return {
    latitude,
    longitude,
    altitude: null,
    accuracy: 5,
    altitudeAccuracy: null,
    heading: null,
    speed: null,
  } as GeolocationCoordinates;
}

describe('StartlineService', () => {
  let service: StartlineService;
  let locationStub: {
    current: GeolocationCoordinates | null;
    curCoordinates: GeolocationCoordinates | null;
    curLatitude: number | null;
    curLongitude: number | null;
  };

  const pinEnd = coords(51.143547, 5.833524);
  const boadEnd = coords(51.139998, 5.839311);
  const boatPosition = coords(51.140391, 5.833951);

  beforeEach(() => {
    locationStub = {
      current: null,
      get curCoordinates() {
        return this.current;
      },
      get curLatitude() {
        return this.current?.latitude ?? null;
      },
      get curLongitude() {
        return this.current?.longitude ?? null;
      },
    };

    TestBed.configureTestingModule({
      providers: [{ provide: LocationService, useValue: locationStub }],
    });
    service = TestBed.inject(StartlineService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should toggle pin and boad end state when setting and clearing positions', () => {
    expect(service.pinEndSet).toBeFalse();
    expect(service.boadEndSet).toBeFalse();

    locationStub.current = pinEnd;
    service.setPinEndPosition();
    locationStub.current = boadEnd;
    service.setBoadEndPosition();

    expect(service.pinEndSet).toBeTrue();
    expect(service.boadEndSet).toBeTrue();
    expect(service.pinEndPosition).toBe(pinEnd);
    expect(service.boadEndPosition).toBe(boadEnd);

    service.clearPinEndPosition();
    service.clearBoadEndPosition();

    expect(service.pinEndSet).toBeFalse();
    expect(service.boadEndSet).toBeFalse();
  });

  it('should return null for start line length until both ends are set', () => {
    expect(service.startLineLength).toBeNull();

    locationStub.current = pinEnd;
    service.setPinEndPosition();

    expect(service.startLineLength).toBeNull();
  });

  it('should calculate start line length once both ends are set', () => {
    locationStub.current = pinEnd;
    service.setPinEndPosition();
    locationStub.current = boadEnd;
    service.setBoadEndPosition();

    expect(service.startLineLength).toBeCloseTo(565, 0);
  });

  it('should calculate distance and time to the start line', () => {
    locationStub.current = pinEnd;
    service.setPinEndPosition();
    locationStub.current = boadEnd;
    service.setBoadEndPosition();
    locationStub.current = boatPosition;
    service.vmgBoatSpeed = 2;

    expect(service.distanceToLine).toBeCloseTo(230, 0);
    expect(service.timeToLine).toBeCloseTo(service.distanceToLine! / 2, 6);
  });

  it('should return null for distance and time when data is incomplete', () => {
    expect(service.distanceToLine).toBeNull();
    expect(service.timeToLine).toBeNull();
  });
});
