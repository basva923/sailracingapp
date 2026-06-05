import { ComponentFixture, TestBed } from '@angular/core/testing';

import { HomeComponent } from './home.component';
import { LocationService } from '../services/location.service';
import { TimerService } from '../services/timer.service';
import { StartlineService } from '../services/startline.service';
import { WindService } from '../services/wind.service';

describe('HomeComponent', () => {
  let component: HomeComponent;
  let fixture: ComponentFixture<HomeComponent>;

  beforeEach(async () => {
    jasmine.clock().install();
    await TestBed.configureTestingModule({
      imports: [HomeComponent],
      providers: [
        {
          provide: LocationService,
          useValue: {
            curLatitude: 51,
            curLongitude: 5,
            curSpeed: 0,
            heading: 90,
            coordinatesLog: [],
            subscribeForLocation: () => () => {},
          },
        },
        { provide: TimerService, useValue: { milliSecondsLeft: null } },
        {
          provide: StartlineService,
          useValue: {
            pinEndSet: false,
            boadEndSet: false,
            startLineLength: null,
            distanceToLine: null,
            timeToLine: null,
            vmgBoatSpeed: 1.5,
          },
        },
        {
          provide: WindService,
          useValue: {
            getCalculatedWindDirection: () => 135,
            getWindDirection: () => 90,
            getRelativeWindDirectionHistory: () => [],
            getWindDirectionFrequencyPart: () => [],
            getRelativeCalculatedWindDirection: () => 45,
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(HomeComponent);
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

  it('should expose default tab and height values', () => {
    expect(component.activeTab).toBe(1);
    expect(component.contentHeight).toBe('93vh');
    expect(component.fullTabsHeight).toBe('100vh');
  });

  it('should adjust heights when the footer is visible', () => {
    (component as any).showFooter = true;

    expect(component.contentHeight).toBe('83vh');
    expect(component.fullTabsHeight).toBe('90vh');
  });
});
