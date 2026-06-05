import { Component, Input, ChangeDetectionStrategy } from '@angular/core';
import { LngLatLike, Map, MapMouseEvent, MapTouchEvent, NavigationControl } from 'maplibre-gl';
import { LocationEvent, LocationService } from '../services/location.service';
import { Util } from '../util/util';
import {
  AttributionControlDirective,
  ControlComponent,
  FullscreenControlDirective,
  GeolocateControlDirective,
  MapComponent,
  NavigationControlDirective,
  MarkerComponent,
  Position,
  ScaleControlDirective,
  ImageComponent
} from '@maplibre/ngx-maplibre-gl';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';


@Component({
  selector: 'app-maplibre-map',
  standalone: true,
  imports: [

    MapComponent,
    ControlComponent,
    MatButtonModule,
    AttributionControlDirective,
    FullscreenControlDirective,
    GeolocateControlDirective,
    NavigationControlDirective,
    ScaleControlDirective,
    MarkerComponent,
    ImageComponent,
    MatIconModule

  ],
  templateUrl: './maplibre-map.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './maplibre-map.component.css'
})
export class MapLibreMapComponent {
  @Input() set height(value: string) {
    this._height = value;
  }


  map: Map | null = null;

  protected center: LngLatLike = [0, 0];
  protected zoom: [number] = [12];
  protected northUp = true;
  protected mapRotated = false;
  protected _height = '100%';

  moved: boolean = false;

  constructor(private locationService: LocationService) {
    if (this.locationService.curLatitude && this.locationService.curLongitude) {
      this.center = [
        this.locationService.curLongitude,
        this.locationService.curLatitude,
      ];
    }
  }

  mapCreated(map: Map) {
    console.log('Map created');
    this.map = map;
    if (this.map) {
      this.locationService.subscribeForLocation((position: GeolocationPosition) => {
        this.updateBearing(position);
        this.updateCurrentLocation(position);
        this.updateCenter(position);
        this.updateTrack(position);
      });
    }
  }

  updateTrack(locationEvent: GeolocationPosition) {
    if (!this.map?.getSource('track-log')) {
      this.addTrackLayer();
    }
    (this.map?.getSource('track-log') as any)?.setData(this.trackData);
  }

  updateCurrentLocation(locationEvent: GeolocationPosition) {
    if (!this.map?.getSource('current-location')) {
      this.addCurrentLocationMarker();
    }
    (this.map?.getSource('current-location') as any)?.setData(this.currentLocationData);
    const rotation = Util.normaliseDegrees(this.locationService.heading - this.map?.getBearing()!);
    this.map?.setLayoutProperty('current-location', 'icon-rotate', rotation);
  }

  updateCenter(locationEvent: GeolocationPosition) {
    if (!this.moved) {
      this.center = [
        locationEvent.coords.longitude,
        locationEvent.coords.latitude
      ];
      this.map?.setCenter(this.center);
    }
  }

  updateBearing(locationEvent: GeolocationPosition) {
    if (this.map && !this.northUp && !this.mapRotated) {
      this.map.setBearing(this.locationService.heading);
    }
  }

  toggleNorth() {
    this.mapRotated = false;
    this.northUp = !this.northUp;
    if (this.northUp) {
      this.map?.setBearing(0);
    } else {
      this.map?.setBearing(this.locationService.heading);
    }
  }


  zoomIn() {
    if (this.map) {
      this.map.zoomIn();
    }
  }

  zoomOut() {
    if (this.map) {
      this.map.zoomOut();
    }
  }

  protected addTrackLayer() {
    if (this.map) {
      this.map.addSource('track-log', {
        type: 'geojson',
        data: this.trackData as any,
      });
      this.map.addLayer({
        id: 'track-log',
        type: 'line',
        source: 'track-log',
        'layout': {
          'line-join': 'round',
          'line-cap': 'round'
        },
        'paint': {
          'line-color': '#888',
          'line-width': 3
        }
      });
    }
  }

  protected addCurrentLocationMarker() {
    if (this.map) {
      this.map!.addSource('current-location', {
        type: 'geojson',
        data: this.currentLocationData as any,
      });
      this.map!.addLayer({
        id: 'current-location',
        type: 'symbol',
        source: 'current-location',
        layout: {
          'icon-image': 'arrow',
          "icon-size": 0.25,
          "icon-rotate": 90
        }
      });
    }
  }


  onTouchEvent(event: TouchEvent | MouseEvent | null = null) {
    if (!this.map || !event) {
      return;
    }
    const target = event.target as HTMLElement;
    if (!target) {
      return;
    }
    if (target.ariaLabel === 'Map') {
      this.moved = true;
      this.mapRotated = true;
    }
  }

  setCenterToCurrentLocation() {
    if (this.locationService.curLatitude && this.locationService.curLongitude) {
      this.center = [
        this.locationService.curLongitude,
        this.locationService.curLatitude
      ];
    }
    this.moved = false;
    return true;
  }

  get trackData() {
    const coordinates = this.locationService.coordinatesLog.map((coords) => [
      coords.longitude,
      coords.latitude,
    ]);

    return {
      type: 'Feature',
      properties: {},
      geometry: {
        type: 'LineString',
        coordinates: coordinates,
      },
    }
  }


  get currentLocationData() {
    return {
      type: 'Feature',
      properties: {},
      geometry: {
        type: 'Point',
        coordinates: [
          this.locationService.curLongitude,
          this.locationService.curLatitude,
        ],
      },
    };
  }

  // get layers() {
  //   return [
  //     polyline(
  //       this.locationService.coordinatesLog.map((coords) => {
  //         return [coords.latitude, coords.longitude];
  //       })
  //     ),
  //   ];
  // }
}
