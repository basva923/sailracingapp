package com.sailracing.app.sensors

import com.sailracing.domain.geo.Angles
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The compass heading of a phone that stands upright, strapped to the mast with its screen facing aft:
 * the direction the back of the phone faces, which is where the bow points. Which way up the phone is
 * (portrait or landscape) makes no difference. Only when the phone lies nearly flat does that direction
 * say nothing, and the top of the phone is used instead, like a hand-held compass.
 */
object DeviceHeading {

    /**
     * Below this horizontal component of the back-of-phone axis (the sine of its tilt from vertical) the
     * phone counts as lying flat: about 17 degrees.
     */
    const val FLAT_THRESHOLD = 0.3f

    /**
     * @param rotation the 3x3 row-major matrix from [android.hardware.SensorManager.getRotationMatrixFromVector],
     *   which maps device axes (x right, y up the screen, z out of the screen) onto world axes (east, north, up).
     */
    fun fromRotationMatrix(rotation: FloatArray): Double {
        // The third column is the device z axis in world coordinates; the back of the phone points the other way.
        val backEast = -rotation[2]
        val backNorth = -rotation[5]
        val upright = hypot(backEast, backNorth) >= FLAT_THRESHOLD
        val east = if (upright) backEast else rotation[1]
        val north = if (upright) backNorth else rotation[4]
        return Angles.normalize(Math.toDegrees(atan2(east.toDouble(), north.toDouble())))
    }
}
