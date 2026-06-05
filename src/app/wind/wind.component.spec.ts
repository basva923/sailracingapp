import { ComponentFixture, TestBed } from '@angular/core/testing';

import { WindComponent } from './wind.component';
import { LocationService } from '../services/location.service';
import { WindService } from '../services/wind.service';

describe('WindComponent', () => {
  let component: WindComponent;
  let fixture: ComponentFixture<WindComponent>;
  let locationService: {
    heading: number;
    curSpeed: number | null;
    phoneIsPointingForward: boolean;
    subscribeForLocation: jasmine.Spy;
  };
  let windService: jasmine.SpyObj<WindService> & { angleOfAttack: number };

  beforeEach(async () => {
    jasmine.clock().install();
    locationService = {
      heading: 90,
      curSpeed: 2,
      phoneIsPointingForward: true,
      subscribeForLocation: jasmine.createSpy('subscribeForLocation').and.returnValue(() => {}),
    };
    windService = jasmine.createSpyObj<WindService>('WindService', [
      'getCalculatedWindDirection',
      'getWindDirection',
      'getRelativeWindDirectionHistory',
      'getWindDirectionFrequencyPart',
      'getRelativeCalculatedWindDirection',
      'setPortTack',
      'setStarboardTack',
      'setWindDirection',
      'resetWindDirectionFrequency',
    ]) as jasmine.SpyObj<WindService> & { angleOfAttack: number };
    windService.angleOfAttack = 45;
    windService.getCalculatedWindDirection.and.returnValue(135);
    windService.getWindDirection.and.returnValue(180);
    windService.getRelativeWindDirectionHistory.and.returnValue([1, 2, 3]);
    windService.getWindDirectionFrequencyPart.and.returnValue([{ x: 0, y: 1 }]);
    windService.getRelativeCalculatedWindDirection.and.returnValue(45);

    await TestBed.configureTestingModule({
      imports: [WindComponent],
      providers: [
        { provide: LocationService, useValue: locationService },
        { provide: WindService, useValue: windService },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WindComponent);
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

  it('should format live wind and boat values', () => {
    component.handleUpdate();

    expect(component.headingText).toBe('90°');
    expect(component.calculatedWindDirectionText).toBe('135°');
    expect(component.speedText).toBe('3.9kt');
    expect(component.configuredWindText).toBe('180°');
  });

  it('should delegate tack and reset actions to services', () => {
    component.setPortTack();
    component.setStarboardTack();
    component.resetWindFrequency();

    expect(windService.setPortTack).toHaveBeenCalled();
    expect(windService.setStarboardTack).toHaveBeenCalled();
    expect(windService.resetWindDirectionFrequency).toHaveBeenCalled();
  });

  it('should toggle phone direction', () => {
    component.reservePhone();

    expect(locationService.phoneIsPointingForward).toBeFalse();
  });
});
