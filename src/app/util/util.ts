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

  // Calculate initial bearing between two points
  static bearing(lat1: number, lon1: number, lat2: number, lon2: number) {
    const lat1Rad = this.toRadians(lat1);
    const lat2Rad = this.toRadians(lat2);
    const dLon = this.toRadians(lon2 - lon1);

    const y = Math.sin(dLon) * Math.cos(lat2Rad);
    const x =
      Math.cos(lat1Rad) * Math.sin(lat2Rad) -
      Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLon);

    return Math.atan2(y, x); // Bearing in radians
  }

  // Calculate cross-track distance from point to great circle
  static crossTrackDistance(
    lat1: number,
    lon1: number,
    lat2: number,
    lon2: number,
    lat3: number,
    lon3: number
  ) {
    const R = 6371; // Earth's radius in kilometers

    // Distance between point A (lat1, lon1) and point P (lat3, lon3)
    const d13 = this.haversineDistanceBetweenPoints(lat1, lon1, lat3, lon3);

    // Bearings
    const θ13 = this.bearing(lat1, lon1, lat3, lon3); // Bearing from A to P
    const θ12 = this.bearing(lat1, lon1, lat2, lon2); // Bearing from A to B

    // Cross-track distance formula
    const dXt = Math.asin(Math.sin(d13 / R) * Math.sin(θ13 - θ12)) * R;

    return Math.abs(dXt); // Return the distance in kilometers
  }

  static haversineDistanceBetweenPoints(
    lat1: number,
    lon1: number,
    lat2: number,
    lon2: number
  ) {
    const R = 6371; // Earth's radius in kilometers
    const dLat = this.toRadians(lat2 - lat1);
    const dLon = this.toRadians(lon2 - lon1);
    const lat1Rad = this.toRadians(lat1);
    const lat2Rad = this.toRadians(lat2);

    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(lat1Rad) *
        Math.cos(lat2Rad) *
        Math.sin(dLon / 2) *
        Math.sin(dLon / 2);

    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c; // Distance in kilometers
  }

  static normaliseDegrees(degrees: number) {
    return ((degrees % 360) + 360) % 360;
  }

  static toRadians(degrees: number) {
    return (degrees * Math.PI) / 180;
  }
}
