import { Util } from './util';

describe('Util', () => {
  it('should create an instance', () => {
    expect(new Util()).toBeTruthy();
  });

  // test to radians
  it('should convert degrees to radians', () => {
    expect(Util.toRadians(90)).toBeCloseTo(1.5708, 4);
    expect(Util.toRadians(180)).toBeCloseTo(3.1416, 4);
    expect(Util.toRadians(270)).toBeCloseTo(4.7124, 4);
    expect(Util.toRadians(360)).toBeCloseTo(6.2832, 4);
  });

  // test normalise degrees
  it('should normalise degrees', () => {
    expect(Util.normaliseDegrees(0)).toBe(0);
    expect(Util.normaliseDegrees(360)).toBe(0);
    expect(Util.normaliseDegrees(361)).toBe(1);
    expect(Util.normaliseDegrees(720)).toBe(0);
    expect(Util.normaliseDegrees(-1)).toBe(359);
    expect(Util.normaliseDegrees(-360)).toBe(0);
    expect(Util.normaliseDegrees(-361)).toBe(359);
  });

  // test angle diff
  it('should calculate angle difference', () => {
    expect(Util.angleDiff(0, 0)).toBe(0);
    expect(Util.angleDiff(0, 90)).toBe(90);
    expect(Util.angleDiff(90, 0)).toBe(-90);
    // expect(Util.angleDiff(0, 180)).toBe(180);
    // expect(Util.angleDiff(180, 0)).toBe(180);
    expect(Util.angleDiff(0, 270)).toBe(-90);
    expect(Util.angleDiff(270, 0)).toBe(90);
    expect(Util.angleDiff(0, 360)).toBe(0);
    expect(Util.angleDiff(360, 0)).toBe(0);
    expect(Util.angleDiff(0, 450)).toBe(90);
    expect(Util.angleDiff(450, 0)).toBe(-90);
    expect(Util.angleDiff(0, 30)).toBe(30);
    expect(Util.angleDiff(30, 0)).toBe(-30);
    expect(Util.angleDiff(30, 60)).toBe(30);
    expect(Util.angleDiff(60, 30)).toBe(-30);
    expect(Util.angleDiff(30, 270)).toBe(-120);
    expect(Util.angleDiff(270, 30)).toBe(120);
    expect(Util.angleDiff(30, 720)).toBe(-30);
    expect(Util.angleDiff(720, 30)).toBe(30);
  });

  // test haversine distance
  it('should calculate haversine distance', () => {
    const lat1 = 51.143547;
    const lon1 = 5.833524;
    const lat2 = 51.139998;
    const lon2 = 5.839311;
    const lat3 = 51.140391;
    const lon3 = 5.833951;

    expect(
      Util.haversineDistanceBetweenPoints(lat1, lon1, lat2, lon2)
    ).toBeCloseTo(565, 0);

    expect(
      Util.haversineDistanceBetweenPoints(lat2, lon2, lat1, lon1)
    ).toBeCloseTo(565, 0);

    // 51.140391°N 5.833951°E
    expect(
      Util.haversineDistanceBetweenPoints(lat3, lon3, lat2, lon2)
    ).toBeCloseTo(376, 0);

    expect(
      Util.haversineDistanceBetweenPoints(lat2, lon2, lat3, lon3)
    ).toBeCloseTo(376, 0);

    expect(
      Util.haversineDistanceBetweenPoints(lat1, lon1, lat3, lon3)
    ).toBeCloseTo(352, 0);

    expect(
      Util.haversineDistanceBetweenPoints(lat3, lon3, lat1, lon1)
    ).toBeCloseTo(352, 0);
  });

  // test cross track distance
  it('should calculate cross track distance', () => {
    const lat1 = 51.143547;
    const lon1 = 5.833524;
    const lat2 = 51.139998;
    const lon2 = 5.839311;
    const lat3 = 51.140391;
    const lon3 = 5.833951;

    expect(Util.distanceToLine(lat1, lon1, lat2, lon2, lat3, lon3)).toBeCloseTo(
      230,
      0
    );

    expect(Util.distanceToLine(lat2, lon2, lat1, lon1, lat3, lon3)).toBeCloseTo(
      230,
      0
    );

    expect(Util.distanceToLine(lat3, lon3, lat2, lon2, lat1, lon1)).toBeCloseTo(
      345,
      0
    );

    expect(Util.distanceToLine(lat1, lon1, lat3, lon3, lat2, lon2)).toBeCloseTo(
      369,
      0
    );

    expect(Util.distanceToLine(lat2, lon2, lat3, lon3, lat1, lon1)).toBeCloseTo(
      345,
      0
    );

    expect(Util.distanceToLine(lat3, lon3, lat1, lon1, lat2, lon2)).toBeCloseTo(
      369,
      0
    );
  });
});
