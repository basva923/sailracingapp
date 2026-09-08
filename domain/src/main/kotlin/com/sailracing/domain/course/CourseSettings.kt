package com.sailracing.domain.course

/**
 * Everything that can be tuned about the course the map draws and the line it plans: how the racing area
 * is cut up ([GridSettings]), what the wind over it is believed to be like ([WindFieldSettings]), and how
 * the race line is searched for ([RaceLineSettings]). Held together in one place so that a course model
 * takes one settings object, and so that a change to any of it makes the model rebuild.
 */
public data class CourseSettings(
    val grid: GridSettings = GridSettings(),
    val windField: WindFieldSettings = WindFieldSettings(),
    val raceLine: RaceLineSettings = RaceLineSettings(),
)
