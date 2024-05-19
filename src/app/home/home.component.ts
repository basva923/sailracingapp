import { Component } from '@angular/core';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { TimerComponent } from '../timer/timer.component';
import { WindComponent } from '../wind/wind.component';
import { MapComponent } from '../map/map.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [TimerComponent, NgbNavModule, WindComponent, MapComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
})
export class HomeComponent {
  activeTab = 1;
}
