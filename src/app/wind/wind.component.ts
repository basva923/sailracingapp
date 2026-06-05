import { Component, AfterViewInit, ChangeDetectionStrategy, OnDestroy } from '@angular/core';
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
  ApexNonAxisChartSeries,
} from 'ng-apexcharts';
import { ViewChild } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { MatGridListModule } from '@angular/material/grid-list';

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
  imports: [
    NgApexchartsModule,
    MatCardModule,
    MatButtonModule,
    MatDividerModule,
    MatGridListModule,
  ],
  templateUrl: './wind.component.html',
  changeDetection: ChangeDetectionStrategy.Eager,
  styleUrl: './wind.component.css',
})
export class WindComponent implements OnDestroy {
  @ViewChild('chart', { static: false }) chartComponent: ChartComponent | undefined;
  @ViewChild('chartWF', { static: false }) chartWFComponent: ChartComponent | undefined;
  public chartOptions: ChartOptions = {
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
  public chartOptionsWF: ChartOptions = {
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
    annotations: {
      xaxis: [
        {
          x: 0,
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
          x: 1,
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
    },
  };

  headingText: string = '360°';
  calculatedWindDirectionText: string = '360°';
  speedText: string = '00kt';
  configuredWindText: string = '360°';
  private updateIntervalId?: ReturnType<typeof setInterval>;
  private chartUpdateIntervalId?: ReturnType<typeof setInterval>;
  private frequencyChartUpdateIntervalId?: ReturnType<typeof setInterval>;
  private unsubscribeForLocation?: () => void;
  private readonly visibilityChangeHandler = () => this.handleVisibilityChange();

  constructor(
    private locationService: LocationService,
    private windService: WindService
  ) {

    this.unsubscribeForLocation = locationService.subscribeForLocation((location: GeolocationPosition) => {
      if (!document.hidden) {
        this.handleUpdate();
      }
    });
    document.addEventListener('visibilitychange', this.visibilityChangeHandler);
    this.startIntervals();
    this.handleUpdate();
  }

  ngOnDestroy() {
    document.removeEventListener('visibilitychange', this.visibilityChangeHandler);
    this.stopIntervals();
    this.unsubscribeForLocation?.();
  }

  private startIntervals() {
    if (document.hidden || this.updateIntervalId) {
      return;
    }

    this.updateIntervalId = setInterval(() => {
      this.handleUpdate();
    }, 500);

    this.chartUpdateIntervalId = setInterval(() => {
      this.handleChartUpdate();
    }, 1000);

    this.frequencyChartUpdateIntervalId = setInterval(() => {
      this.handleFrequencyChartUpdate();
    }, 1000);
  }

  private stopIntervals() {
    clearInterval(this.updateIntervalId);
    clearInterval(this.chartUpdateIntervalId);
    clearInterval(this.frequencyChartUpdateIntervalId);
    this.updateIntervalId = undefined;
    this.chartUpdateIntervalId = undefined;
    this.frequencyChartUpdateIntervalId = undefined;
  }

  private handleVisibilityChange() {
    if (document.hidden) {
      this.stopIntervals();
    } else {
      this.handleUpdate();
      this.handleChartUpdate();
      this.handleFrequencyChartUpdate();
      this.startIntervals();
    }
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
  }

  handleChartUpdate() {
    this.chartComponent?.updateSeries(
      [
        {
          data: this.windService.getRelativeWindDirectionHistory().slice(-30),
        },
      ],
      false
    );
  }

  handleFrequencyChartUpdate() {
    this.chartWFComponent?.updateSeries(
      [
        {
          data: this.windService.getWindDirectionFrequencyPart(),
        },
      ],
      false
    );

    if (this.windService.getRelativeCalculatedWindDirection()) {
      this.chartOptionsWF.annotations!.xaxis![1].x =
        this.windService.getRelativeCalculatedWindDirection();
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
