import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TimerComponent } from './timer.component';

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
});
