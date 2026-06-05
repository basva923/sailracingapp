import { UnitToString } from './unit-to-string';

describe('UnitToString', () => {
  it('should create an instance', () => {
    expect(new UnitToString()).toBeTruthy();
  });

  it('should format meter values without decimals', () => {
    expect(UnitToString.metersToString(12.4)).toBe('12m');
    expect(UnitToString.metersToString(12.5)).toBe('13m');
  });

  it('should format fraction digit meter values using current rounding behaviour', () => {
    expect(UnitToString.fractionDigits(12.4, 2)).toBe('12m');
    expect(UnitToString.fractionDigits(12.5, 3)).toBe('13m');
  });

  it('test milisecondsToTime', () => {
    expect(UnitToString.milisecondsToTime(1000)).toBe('00:01');
    expect(UnitToString.milisecondsToTime(60000)).toBe('01:00');
    expect(UnitToString.milisecondsToTime(3600000)).toBe('01:00:00');
    expect(UnitToString.milisecondsToTime(86400000)).toBe('1d 00:00:00');
  });

  it('should pad seconds, minutes, hours, and days', () => {
    expect(UnitToString.milisecondsToTime(9000)).toBe('00:09');
    expect(UnitToString.milisecondsToTime(9 * 60000 + 7000)).toBe('09:07');
    expect(UnitToString.milisecondsToTime(2 * 3600000 + 3 * 60000 + 4000)).toBe('02:03:04');
    expect(UnitToString.milisecondsToTime(2 * 86400000 + 3 * 3600000 + 4 * 60000 + 5000)).toBe('2d 03:04:05');
  });

  it('should format speeds, durations, distances, and degrees', () => {
    expect(UnitToString.metersPerSecondToKnots(1)).toBe('1.9kt');
    expect(UnitToString.metersPerSecondToKnots(2.5)).toBe('4.9kt');
    expect(UnitToString.secondsToString(12.4)).toBe('12s');
    expect(UnitToString.secondsToString(12.5)).toBe('13s');
    expect(UnitToString.metersToNauticalMiles(1852)).toBe('1.00nm');
    expect(UnitToString.metersToNauticalMiles(926)).toBe('0.50nm');
    expect(UnitToString.degreesToString(12.4)).toBe('12°');
    expect(UnitToString.degreesToString(12.5)).toBe('13°');
  });
});
