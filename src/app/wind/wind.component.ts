import { Component } from '@angular/core';
import { LocationService } from '../services/location.service';
import { WindService } from '../services/wind.service';
import { UnitToString } from '../util/unit-to-string';
import {
  NgApexchartsModule,
  ChartComponent,
  ApexAxisChartSeries,
  ApexTitleSubtitle,
  ApexChart,
  ApexXAxis,
  ApexYAxis,
  ApexAnnotations,
} from 'ng-apexcharts';
import { ViewChild } from '@angular/core';

export type ChartOptions = {
  series: ApexAxisChartSeries;
  chart: ApexChart;
  title: ApexTitleSubtitle;
  xaxis: ApexXAxis;
  yaxis: ApexYAxis;
  annotations: ApexAnnotations;
};

@Component({
  selector: 'app-wind',
  standalone: true,
  imports: [NgApexchartsModule],
  templateUrl: './wind.component.html',
  styleUrl: './wind.component.css',
})
export class WindComponent {
  // @ViewChild('chart', { static: false }) char  t!: ChartComponent;
  public chartOptions: Partial<ChartOptions>;
  public chartOptionsWF: Partial<ChartOptions>;

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
    this.chartOptions = {
      series: [
        {
          name: 'Wind Direction History',
          data: [44, 55, 13, 33],
        },
      ],
      chart: {
        type: 'line',
      },
      title: {
        text: 'Wind Directorion History',
      },
      xaxis: { labels: { show: false } },
      yaxis: { decimalsInFloat: 0 },
      annotations: {},
    };
    this.chartOptionsWF = {
      series: [
        {
          name: 'Wind Direction Frequency',
          data: [44, 55, 13, 33],
        },
      ],
      chart: {
        type: 'line',
      },
      title: {
        text: 'Wind Directorion Frequency',
      },
      xaxis: { labels: { show: true, hideOverlappingLabels: true } },
      yaxis: { decimalsInFloat: 0 },
      annotations: {},
    };

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
      this.windService.getCalculatedWindDirection()
    );
    this.speedText = UnitToString.metersPerSecondToKnots(
      this.locationService.curSpeed ? this.locationService.curSpeed : 0
    );

    this.configuredWindText = UnitToString.degreesToString(
      this.windService.getWindDirection()
    );

    this.chartOptions.series = [
      {
        data: this.windService.getWindDirectionHistory().slice(-30),
      },
    ];

    this.chartOptionsWF.series = [
      {
        data: this.windService.getWindDirectionFrequencyPart(),
      },
    ];

    if (this.windService.getCalculatedWindDirection()) {
      this.chartOptionsWF.annotations = {
        xaxis: [
          {
            x: this.windService.getWindDirection(),
            strokeDashArray: 0,
            borderColor: '#775DD0',
            label: {
              borderColor: '#775DD0',
              style: {
                color: '#fff',
                background: '#775DD0',
              },
              text: 'Configured wind',
            },
          },
          {
            x: this.windService.getCalculatedWindDirection(),
            strokeDashArray: 0,
            borderColor: '#B3F7CA',
            label: {
              borderColor: '#B3F7CA',
              style: {
                color: '#fff',
                background: '#B3F7CA',
              },
              text: 'Calculated wind',
            },
          },
        ],
      };
    }
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

  reservePhone() {
    this.locationService.phoneIsPointingForward =
      !this.locationService.phoneIsPointingForward;
  }

  resetWindFrequency() {
    this.windService.resetWindDirectionFrequency();
  }

  get title() {
    return {
      text: 'My First Angular Chart',
    };
  }
}
