export class UnitToString {
  static metersToString(meters: number): string {
    return meters.toFixed(0) + 'm';
  }
  static fractionDigits(meters: number, fractionDigits: number): string {
    return meters.toFixed() + 'm';
  }
  static milisecondsToTime(ms: number) {
    const seconds = Math.floor((ms / 1000) % 60);
    const minutes = Math.floor((ms / (1000 * 60)) % 60);
    const hours = Math.floor((ms / (1000 * 60 * 60)) % 24);
    const days = Math.floor(ms / (1000 * 60 * 60 * 24));

    const pad = (num: number) => num.toString().padStart(2, '0');

    return (
      (days > 0 ? days + 'd ' : '') +
      (hours > 0 || days > 0 ? pad(hours) + ':' : '') +
      pad(minutes) +
      ':' +
      pad(seconds)
    );
  }

  static metersPerSecondToKnots(mps: number): string {
    return (mps * 1.94384449).toFixed(1) + 'kt';
  }

  static secondsToString(seconds: number): string {
    return seconds.toFixed(0) + 's';
  }

  static metersToNauticalMiles(m: number): string {
    return (m / 1852).toFixed(2) + 'nm';
  }

  static degreesToString(degrees: number): string {
    return degrees.toFixed(0) + '°';
  }
}
