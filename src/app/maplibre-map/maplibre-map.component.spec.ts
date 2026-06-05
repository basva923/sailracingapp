import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MapLibreMapComponent } from './maplibre-map.component';
import { LocationService } from '../services/location.service';

describe('MapboxMapComponent', () => {
  let component: MapLibreMapComponent;
  let fixture: ComponentFixture<MapLibreMapComponent>;
  let locationService: {
    curLatitude: number | null;
    curLongitude: number | null;
    heading: number;
    coordinatesLog: GeolocationCoordinates[];
    subscribeForLocation: jasmine.Spy;
  };

  beforeEach(async () => {
    locationService = {
      curLatitude: 51,
      curLongitude: 5,
      heading: 90,
      coordinatesLog: [
        { latitude: 51, longitude: 5 } as GeolocationCoordinates,
        { latitude: 52, longitude: 6 } as GeolocationCoordinates,
      ],
      subscribeForLocation: jasmine.createSpy('subscribeForLocation').and.returnValue(() => {}),
    };

    await TestBed.configureTestingModule({
      imports: [MapLibreMapComponent],
      providers: [{ provide: LocationService, useValue: locationService }],
    }).compileComponents();

    fixture = TestBed.createComponent(MapLibreMapComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should apply height input and center on current location', () => {
    component.height = '50vh';

    expect((component as any)._height).toBe('50vh');
    expect(component.setCenterToCurrentLocation()).toBeTrue();
    expect((component as any).center).toEqual([5, 51]);
  });

  it('should expose GeoJSON for current location and track log', () => {
    expect(component.currentLocationData.geometry.coordinates).toEqual([5, 51]);
    expect(component.trackData.geometry.coordinates).toEqual([
      [5, 51],
      [6, 52],
    ]);
  });
});
