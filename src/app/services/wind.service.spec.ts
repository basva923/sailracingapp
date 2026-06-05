import { TestBed } from '@angular/core/testing';

import { WindService } from './wind.service';
import { LocationService } from './location.service';
import { Util } from '../util/util';

describe('WindService', () => {
  let service: WindService;
  let locationStub: {
    headingValue: number;
    heading: number;
    subscribeForLocation: jasmine.Spy;
  };

  beforeEach(() => {
    jasmine.clock().install();
    locationStub = {
      headingValue: 100,
      get heading() {
        return this.headingValue;
      },
      subscribeForLocation: jasmine.createSpy('subscribeForLocation').and.returnValue(() => {}),
    };

    TestBed.configureTestingModule({
      providers: [{ provide: LocationService, useValue: locationStub }],
    });
    service = TestBed.inject(WindService);
  });

  afterEach(() => {
    jasmine.clock().uninstall();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
    expect(locationStub.subscribeForLocation).toHaveBeenCalled();
  });

  it('should set and get a configured wind direction', () => {
    service.setWindDirection(222);

    expect(service.getWindDirection()).toBe(222);
  });

  it('should set port and starboard tack wind directions from heading and angle of attack', () => {
    locationStub.headingValue = 20;
    service.angleOfAttack = 45;

    service.setPortTack();
    expect(service.getWindDirection()).toBe(Util.normaliseDegrees(20 - 45));

    service.setStarboardTack();
    expect(service.getWindDirection()).toBe(Util.normaliseDegrees(20 + 45));
  });

  it('should identify upwind and downwind sailing', () => {
    locationStub.headingValue = 100;
    service.setWindDirection(140);

    expect(service.sailingUpwind).toBeTrue();
    expect(service.sailingDownwind).toBeFalse();

    service.setWindDirection(250);

    expect(service.sailingUpwind).toBeFalse();
    expect(service.sailingDownwind).toBeTrue();
  });

  it('should identify port and starboard tack', () => {
    locationStub.headingValue = 100;
    service.setWindDirection(60);

    expect(service.sailingOnPortTack).toBeTrue();
    expect(service.sailingOnStarboardTack).toBeFalse();

    service.setWindDirection(140);

    expect(service.sailingOnPortTack).toBeFalse();
    expect(service.sailingOnStarboardTack).toBeTrue();
  });

  it('should calculate wind direction for starboard upwind', () => {
    locationStub.headingValue = 100;
    service.angleOfAttack = 45;
    service.setWindDirection(140);

    expect(service.getCalculatedWindDirection()).toBe(145);
  });

  it('should calculate wind direction for starboard downwind', () => {
    locationStub.headingValue = 100;
    service.angleOfAttack = 45;
    service.setWindDirection(250);

    expect(service.getCalculatedWindDirection()).toBe(235);
  });

  it('should calculate wind direction for port downwind', () => {
    locationStub.headingValue = 100;
    service.angleOfAttack = 45;
    service.setWindDirection(300);

    expect(service.getCalculatedWindDirection()).toBe(325);
  });

  it('should calculate wind direction for port upwind', () => {
    locationStub.headingValue = 100;
    service.angleOfAttack = 45;
    service.setWindDirection(60);

    expect(service.getCalculatedWindDirection()).toBe(55);
  });

  it('should reset wind direction frequency to 360 zeroes', () => {
    service.resetWindDirectionFrequency();

    expect(service.getWindDirectionFrequency().length).toBe(360);
    expect(service.getWindDirectionFrequency().every((value) => value === 0)).toBeTrue();
  });
});
