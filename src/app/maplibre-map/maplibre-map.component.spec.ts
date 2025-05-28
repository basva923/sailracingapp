import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MapLibreMapComponent } from './maplibre-map.component';

describe('MapboxMapComponent', () => {
  let component: MapLibreMapComponent;
  let fixture: ComponentFixture<MapLibreMapComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MapLibreMapComponent]
    })
      .compileComponents();

    fixture = TestBed.createComponent(MapLibreMapComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
