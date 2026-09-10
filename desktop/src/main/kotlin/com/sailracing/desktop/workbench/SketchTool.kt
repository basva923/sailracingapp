package com.sailracing.desktop.workbench

/** What the mouse lays down on the water. */
enum class SketchTool(val title: String, val hint: String) {
    /** Drag to sail: the boat follows the mouse, and the route is planned from where it stops. */
    TRACK("Track", "Drag to sail the boat. The route is planned from where you let go."),

    /** The end of the start line the fleet leaves to port: the buoy. */
    PIN("Pin end", "Click or drag to lay the pin end of the start line."),

    /** The other end: the committee boat. */
    COMMITTEE("Committee boat", "Click or drag to lay the committee boat end of the start line."),

    /** The mark at the top of the beat, which is what the route is asked for. */
    MARK("Windward mark", "Click or drag to lay the mark the route goes to."),
}
