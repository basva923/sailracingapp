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
});
