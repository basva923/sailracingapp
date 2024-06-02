export class UnitToString {
  static metersToString(meters: number): string {
    return meters.toFixed(2) + 'm';
  }
  static milisecondsToTime(ms: number) {
    const seconds = Math.floor((ms / 1000) % 60);
    const minutes = Math.floor((ms / (1000 * 60)) % 60);
    const hours = Math.floor((ms / (1000 * 60 * 60)) % 24);
    const days = Math.floor(ms / (1000 * 60 * 60 * 24));
    return (
      (days > 0 ? days + 'd ' : '') +
      (hours > 0 ? hours + ':' : '') +
      (minutes >= 0 ? minutes + ':' : '') +
      (seconds >= 0 ? seconds + '' : '')
    );
  }

  static metersPerSecondToKnots(mps: number): string {
    return (mps * 1.94384449).toFixed(2) + 'kt';
  }

  static metersToNauticalMiles(m: number): string {
    return (m / 1852).toFixed(2) + 'nm';
  }

  static degreesToString(degrees: number): string {
    return degrees.toFixed(0) + '°';
  }
}
