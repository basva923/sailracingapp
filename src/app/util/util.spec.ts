import { Util } from './util';

describe('Util', () => {
  it('should create an instance', () => {
    expect(new Util()).toBeTruthy();
  });

  describe('distancePointToLine', () => {
    it('returns the perpendicular distance to a vertical line', () => {
      // Line x = 1 (through (1,0) and (1,5)), point at the origin.
      expect(Util.distancePointToLine(0, 0, 1, 0, 1, 5)).toBeCloseTo(1, 10);
    });

    it('returns the perpendicular distance to a horizontal line', () => {
      // Line y = 0 (through (0,0) and (5,0)), point at (3,4).
      expect(Util.distancePointToLine(3, 4, 0, 0, 5, 0)).toBeCloseTo(4, 10);
    });

    it('returns zero when the point lies on the line', () => {
      // Point (1,2) sits on the vertical line x = 1.
      expect(Util.distancePointToLine(1, 2, 1, 0, 1, 5)).toBeCloseTo(0, 10);
    });
  });

  describe('haversineDistanceBetweenPoints', () => {
    it('returns roughly zero for identical points', () => {
      expect(
        Util.haversineDistanceBetweenPoints(52.1, 4.3, 52.1, 4.3)
      ).toBeLessThan(1);
    });

    it('returns about 111.2 km for one degree of latitude', () => {
      const distance = Util.haversineDistanceBetweenPoints(0, 0, 1, 0);
      // 1 degree of latitude on a 6371 km sphere is ~111195 m.
      expect(distance).toBeCloseTo(111194.93, 0);
    });

    it('is symmetric in its arguments', () => {
      const forward = Util.haversineDistanceBetweenPoints(52.0, 4.0, 52.5, 4.5);
      const backward = Util.haversineDistanceBetweenPoints(52.5, 4.5, 52.0, 4.0);
      expect(forward).toBeCloseTo(backward, 6);
    });
  });
});
