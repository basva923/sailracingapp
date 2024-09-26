import { UnitToString } from './unit-to-string';

describe('UnitToString', () => {
  it('should create an instance', () => {
    expect(new UnitToString()).toBeTruthy();
  });

  it('test milisecondsToTime', () => {
    expect(UnitToString.milisecondsToTime(1000)).toBe('00:01');
    expect(UnitToString.milisecondsToTime(60000)).toBe('01:00');
    expect(UnitToString.milisecondsToTime(3600000)).toBe('01:00:00');
    expect(UnitToString.milisecondsToTime(86400000)).toBe('1d 00:00:00');
  });
});
