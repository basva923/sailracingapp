package com.sailracing.desktop

import com.sailracing.desktop.ui.WorkbenchWindow
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * The strategy workbench: draw a course, set the wind, ask for the route to the windward mark.
 *
 * Run it with `./gradlew :desktop:run`. Everything it answers with comes out of `:domain`, the module the
 * Android app is built on, so what it draws is what the phone would have drawn on the same water.
 */
fun main() {
    runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    SwingUtilities.invokeLater {
        val window = WorkbenchWindow()
        window.isVisible = true
        window.fitCanvas()
    }
}
