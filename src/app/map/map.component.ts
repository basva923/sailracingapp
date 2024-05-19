import { Component } from '@angular/core';
import { LeafletModule } from '@asymmetrik/ngx-leaflet';
import {
  tileLayer,
  latLng,
  circle,
  marker,
  polygon,
  polyline,
  Map,
} from 'leaflet';
import { LocationService } from '../services/location.service';
import { NgIf } from '@angular/common';

@Component({
  selector: 'app-map',
  standalone: true,
  imports: [LeafletModule, NgIf],
  templateUrl: './map.component.html',
  styleUrl: './map.component.css',
})
export class MapComponent {
  options = {
    layers: [
      tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 18,
        attribution: 'Opensteetmap contributors',
      }),
    ],
    zoom: 16,
  };

  center = latLng(0, 0);
  moved: boolean = false;
  map: Map | null = null;
  isProgramaticMove = false;

  constructor(private locationService: LocationService) {
    const self = this;
    if (this.locationService.curLatitude && this.locationService.curLongitude) {
      this.center = latLng(
        this.locationService.curLatitude,
        this.locationService.curLongitude
      );
    }
    locationService.subscribeForLocation((location) => {
      if (!self.moved) {
        self.isProgramaticMove = true;
        self.center = latLng(
          location.coords.latitude,
          location.coords.longitude
        );
      }
    });
  }

  onMapReady(map: Map) {
    this.map = map;
    const self = this;
    map.on('movestart', () => {
      if (!this.isProgramaticMove) this.setMoved();
    });
    map.on('moveend', () => {
      self.isProgramaticMove = false;
    });
  }

  setMoved() {
    console.log('Map moved by user');
    this.moved = true;
  }

  setCenterToCurrentLocation() {
    if (this.locationService.curLatitude && this.locationService.curLongitude) {
      this.isProgramaticMove = true;
      this.center = latLng(
        this.locationService.curLatitude,
        this.locationService.curLongitude
      );
    }
    this.moved = false;
  }

  get layers() {
    return [
      polyline(
        this.locationService.coordinatesLog.map((coords) => {
          return [coords.latitude, coords.longitude];
        })
      ),
    ];
  }
}
