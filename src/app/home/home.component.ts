import { Component, ChangeDetectionStrategy } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { MapLibreMapComponent } from '../maplibre-map/maplibre-map.component';
import { MatIconModule } from '@angular/material/icon';
import { WindComponent } from '../wind/wind.component';
import { TimerComponent } from '../timer/timer.component';


@Component({
  selector: 'app-home',
  standalone: true,
  imports: [TimerComponent, WindComponent, MatTabsModule, MapLibreMapComponent, MatIconModule],
  templateUrl: './home.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './home.component.css',
})
export class HomeComponent {
  activeTab = 1;
  protected showFooter = false;


  get contentHeight(): string {
    return this.showFooter ? '83vh' : '93vh';
  }

  get fullTabsHeight(): string {
    if (this.showFooter) {
      return '90vh';
    } else {
      return '100vh';
    }
  }
}
