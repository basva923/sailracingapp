import { Component } from '@angular/core';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { TimerComponent } from '../timer/timer.component';
import { WindComponent } from '../wind/wind.component';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [TimerComponent, NgbNavModule, WindComponent],
  templateUrl: './home.component.html',
  styleUrl: './home.component.css',
})
export class HomeComponent {
  activeTab = 1;
}
