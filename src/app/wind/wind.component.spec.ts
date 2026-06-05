import { ComponentFixture, TestBed } from '@angular/core/testing';

import { WindComponent } from './wind.component';

function orientation(
  partial: Partial<DeviceOrientationEvent>
): DeviceOrientationEvent {
  return {
    absolute: false,
    alpha: 0,
    beta: 0,
    gamma: 0,
    ...partial,
  } as DeviceOrientationEvent;
}

describe('WindComponent', () => {
  let component: WindComponent;
  let fixture: ComponentFixture<WindComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WindComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(WindComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should remove the orientation listener on destroy', () => {
    const removeListener = spyOn(window, 'removeEventListener').and.callThrough();
    component.ngOnDestroy();
    expect(removeListener).toHaveBeenCalledWith(
      'deviceorientationabsolute',
      jasmine.any(Function),
      true
    );
  });

  it('should cache the heading until the orientation changes', () => {
    component.handleOrientationChange(
      orientation({ absolute: true, alpha: 90, beta: 10, gamma: 5 })
    );
    const first = component.heading;
    // Mutating a raw field without flagging a change must not recompute.
    component.alpha = 180;
    const second = component.heading;
    expect(second).toBe(first);
  });

  describe('heading calculation', () => {
    it('computes 270 degrees for a 90 degree gamma roll', () => {
      component.handleOrientationChange(
        orientation({ alpha: 0, beta: 0, gamma: 90 })
      );
      expect(component.heading).toBeCloseTo(270, 6);
    });

    it('recomputes the heading after a new orientation event', () => {
      let now = 1000;
      spyOn(Date, 'now').and.callFake(() => now);

      component.handleOrientationChange(
        orientation({ alpha: 0, beta: 0, gamma: 90 })
      );
      const first = component.heading;

      now += 500;
      component.handleOrientationChange(
        orientation({ alpha: 10, beta: 20, gamma: 30 })
      );
      const second = component.heading;

      expect(second).not.toBe(first);
    });

    it('stores the orientation angles from the event', () => {
      component.handleOrientationChange(
        orientation({ absolute: true, alpha: 12, beta: 34, gamma: 56 })
      );
      expect(component.absolute).toBeTrue();
      expect(component.alpha).toBe(12);
      expect(component.beta).toBe(34);
      expect(component.gamma).toBe(56);
    });
  });

  describe('throttling', () => {
    it('ignores orientation events that arrive within the throttle window', () => {
      let now = 1000;
      spyOn(Date, 'now').and.callFake(() => now);

      component.handleOrientationChange(orientation({ alpha: 45 }));
      expect(component.alpha).toBe(45);

      // Less than the 200ms throttle interval later: must be ignored.
      now += 100;
      component.handleOrientationChange(orientation({ alpha: 90 }));
      expect(component.alpha).toBe(45);

      // Past the throttle interval: the update is applied.
      now += 200;
      component.handleOrientationChange(orientation({ alpha: 90 }));
      expect(component.alpha).toBe(90);
    });
  });
});
