export class Util {
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

  /**
   * Calculate the distance between point P and the great circle defined by points A and B.
   */
  static distanceToLine(
    latA: number,
    lonA: number,
    latB: number,
    lonB: number,
    latP: number,
    lonP: number
  ) {
    const R = 6371 * 1000; // Earth's radius in meters

    const d13 = this.haversineDistanceBetweenPoints(latA, lonA, latP, lonP);

    // Bearings
    const θ13 = this.bearing(latA, lonA, latP, lonP);
    const θ12 = this.bearing(latA, lonA, latB, lonB);

    // Cross-track distance formula
    const dXt = Math.asin(Math.sin(d13 / R) * Math.sin(θ13 - θ12)) * R;

    return Math.abs(dXt); // Return the distance in meters
  }

  static haversineDistanceBetweenPoints(
    lat1: number,
    lon1: number,
    lat2: number,
    lon2: number
  ) {
    const R = 6371 * 1000; // Earth's radius in meter
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
    return R * c; // Distance in meter
  }

  static normaliseDegrees(degrees: number) {
    return ((degrees % 360) + 360) % 360;
  }

  static toRadians(degrees: number) {
    return (degrees * Math.PI) / 180;
  }

  /**
   * Calculate the difference between two angles in degrees.
   * @returns The difference between the two angles in degrees.
   */
  static angleDiff(degrees1: number, degrees2: number) {
    degrees1 = this.normaliseDegrees(degrees1);
    degrees2 = this.normaliseDegrees(degrees2);

    const diff = degrees2 - degrees1;

    // Normalize the difference to be between -180 and 180
    if (diff >= 180) {
      return diff - 360;
    } else if (diff < -180) {
      return diff + 360;
    } else {
      return diff;
    }
  }
}
