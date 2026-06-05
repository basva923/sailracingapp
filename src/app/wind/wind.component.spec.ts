import { ComponentFixture, TestBed } from '@angular/core/testing';

import { WindComponent } from './wind.component';

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
    component.handleOrientationChange({
      absolute: true,
      alpha: 90,
      beta: 10,
      gamma: 5,
    } as DeviceOrientationEvent);
    const first = component.heading;
    // Mutating a raw field without flagging a change must not recompute.
    component.alpha = 180;
    const second = component.heading;
    expect(second).toBe(first);
  });
});
