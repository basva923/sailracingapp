import { Component } from '@angular/core';
import { LocationService } from '../services/location.service';
import { WindService } from '../services/wind.service';
import { UnitToString } from '../util/unit-to-string';

@Component({
  selector: 'app-wind',
  standalone: true,
  imports: [],
  templateUrl: './wind.component.html',
  styleUrl: './wind.component.css',
})
export class WindComponent {
  setWind() {
    throw new Error('Method not implemented.');
  }
  headingText: string = '360°';
  calculatedWindDirectionText: string = '360°';
  speedText: string = '00kt';
  configuredWindText: string = '360°';

  constructor(
    private locationService: LocationService,
    private windService: WindService
  ) {
    const self = this;
    locationService.subscribeForLocation((location: GeolocationPosition) => {
      self.handleUpdate();
    });
    setInterval(() => {
      self.handleUpdate();
    }, 100);
  }

  handleUpdate() {
    this.headingText = UnitToString.degreesToString(
      this.locationService.heading
    );
    this.calculatedWindDirectionText = UnitToString.degreesToString(
      this.windService.calculatedWindDirection
    );
    this.speedText = UnitToString.metersPerSecondToKnots(
      this.locationService.curSpeed ? this.locationService.curSpeed : 0
    );

    this.configuredWindText = UnitToString.degreesToString(
      this.windService.getWindDirection()
    );
  }

  setPortTack() {
    this.windService.setPortTack();
    this.handleUpdate();
  }

  setStarboardTack() {
    this.windService.setStarboardTack();
    this.handleUpdate();
  }

  configureWind() {
    const windDirection = Number(
      prompt('Enter wind direction in degrees', '0')
    );

    this.windService.setWindDirection(windDirection);
    this.handleUpdate();
  }

  configureAngleOfAttack() {
    const angleOfAttack = Number(
      prompt('Enter angle of attack in degrees', '45')
    );
    this.windService.angleOfAttack = angleOfAttack;
    this.handleUpdate();
  }
}
