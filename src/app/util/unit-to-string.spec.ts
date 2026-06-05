import { UnitToString } from './unit-to-string';

describe('UnitToString', () => {
  it('should create an instance', () => {
    expect(new UnitToString()).toBeTruthy();
  });

  describe('metersToString', () => {
    it('formats a distance with two decimals and a unit suffix', () => {
      expect(UnitToString.metersToString(123.456)).toBe('123.46m');
    });

    it('formats zero', () => {
      expect(UnitToString.metersToString(0)).toBe('0.00m');
    });
  });

  describe('milisecondsToTime', () => {
    it('returns an empty string for zero', () => {
      expect(UnitToString.milisecondsToTime(0)).toBe('');
    });

    it('formats seconds only', () => {
      expect(UnitToString.milisecondsToTime(1000)).toBe('1s');
    });

    it('formats minutes and seconds', () => {
      expect(UnitToString.milisecondsToTime(61000)).toBe('1m 1s');
    });

    it('formats hours, minutes and seconds', () => {
      expect(UnitToString.milisecondsToTime(3661000)).toBe('1h 1m 1s');
    });

    it('formats days, hours, minutes and seconds', () => {
      // 1 day + 1 hour + 1 minute + 1 second.
      const ms = 86400000 + 3600000 + 60000 + 1000;
      expect(UnitToString.milisecondsToTime(ms)).toBe('1d 1h 1m 1s');
    });
  });

  describe('metersPerSecondToKnots', () => {
    it('converts metres per second to knots', () => {
      expect(UnitToString.metersPerSecondToKnots(1)).toBe('1.94kt');
    });

    it('formats zero', () => {
      expect(UnitToString.metersPerSecondToKnots(0)).toBe('0.00kt');
    });
  });

  describe('metersToNauticalMiles', () => {
    it('converts metres to nautical miles', () => {
      expect(UnitToString.metersToNauticalMiles(1852)).toBe('1.00nm');
    });

    it('converts multiple nautical miles', () => {
      expect(UnitToString.metersToNauticalMiles(3704)).toBe('2.00nm');
    });
  });
});
