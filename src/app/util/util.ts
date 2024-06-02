export class Util {
  static distancePointToLine(
    x0: number,
    y0: number,
    x1: number,
    y1: number,
    x2: number,
    y2: number
  ) {
    return (
      Math.abs((x2 - x1) * (y1 - y0) - (x1 - x0) * (y2 - y1)) /
      Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2))
    );
  }

  static haversineDistanceBetweenPoints(
    lat1: number,
    lon1: number,
    lat2: number,
    lon2: number
  ) {
    const R = 6371e3;
    const p1 = (lat1 * Math.PI) / 180;
    const p2 = (lat2 * Math.PI) / 180;
    const deltaLon = lon2 - lon1;
    const deltaLambda = (deltaLon * Math.PI) / 180;
    const d =
      Math.acos(
        Math.sin(p1) * Math.sin(p2) +
          Math.cos(p1) * Math.cos(p2) * Math.cos(deltaLambda)
      ) * R;
    return d;
  }

  static normaliseDegrees(degrees: number) {
    return ((degrees % 360) + 360) % 360;
  }
}
