package com.sailracing.desktop.ui

import com.sailracing.desktop.workbench.Report
import com.sailracing.desktop.workbench.SketchTool
import com.sailracing.desktop.workbench.Workbench
import com.sailracing.domain.sketch.CourseSketch
import com.sailracing.domain.sketch.CourseSketchFormat
import com.sailracing.domain.sketch.SketchAnalysis
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JTextArea
import javax.swing.KeyStroke
import javax.swing.SpinnerNumberModel
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * The workbench window: the chart in the middle, the dials down the side and, underneath, what the app
 * makes of the course in its own words.
 *
 * Asking for a route is a Monte Carlo over a whole racing area, so it is done off the event thread and
 * the answer is shown when it arrives. Answers to a course that has changed since are thrown away, which
 * is what [AtomicLong] counts: a drag that outruns the planner never leaves an older route on the screen.
 */
class WorkbenchWindow : JFrame("Sail racing · strategy workbench") {

    private var workbench = Workbench()
    private var loading = false
    private val planner = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "route-planner").apply { isDaemon = true }
    }
    private val asked = AtomicLong()

    private val canvas = CourseCanvas(workbench, ::changed, ::plan)
    private val report = JTextArea(6, 60).apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        background = Palette.PANEL
        foreground = Palette.TEXT
        border = EmptyBorder(8, 8, 8, 8)
    }

    private val windDirection = spinner(0, 0, 359, 5)
    private val tackAngle = spinner(workbench.sketch.wind.tackAngleDegrees, 20, 80, 1)
    private val penSpeed = JSpinner(SpinnerNumberModel(CourseSketch.DEFAULT_BOAT_SPEED_MPS, 0.5, 12.0, 0.1))
    private val runs = spinner(workbench.sketch.settings.course.raceLine.runs, 1, 64, 4)
    private val cellSize = JComboBox(CELL_SIZES.map { it?.let { metres -> "$metres m" } ?: "Automatic" }.toTypedArray())

    init {
        defaultCloseOperation = EXIT_ON_CLOSE
        contentPane.background = Palette.WATER
        layout = BorderLayout()
        add(canvas, BorderLayout.CENTER)
        add(controls(), BorderLayout.EAST)
        add(JScrollPane(report).apply { border = null }, BorderLayout.SOUTH)
        bindShortcuts()
        showReport()
        pack()
        setLocationRelativeTo(null)
    }

    /** Fits the chart around whatever is drawn. Called once the window has a size to fit into. */
    fun fitCanvas() {
        canvas.fit()
    }

    // --- the dials -------------------------------------------------------------------------------

    private fun controls(): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            background = Palette.PANEL
            border = EmptyBorder(12, 12, 12, 12)
            preferredSize = Dimension(280, 0)
        }
        panel.add(heading("Tool"))
        val group = ButtonGroup()
        for (tool in SketchTool.entries) {
            val button = JRadioButton(tool.title, tool == workbench.tool).apply {
                background = Palette.PANEL
                foreground = Palette.TEXT
                alignmentX = Component.LEFT_ALIGNMENT
                addActionListener { changed(workbench.withTool(tool)) }
            }
            group.add(button)
            panel.add(button)
        }

        panel.add(heading("Reference wind"))
        panel.add(row("Blows from", windDirection))
        windDirection.addChangeListener { if (!loading) replan(workbench.withWindDirection(value(windDirection))) }
        panel.add(row("Tacks through", tackAngle))
        tackAngle.addChangeListener { if (!loading) replan(workbench.withTackAngle(value(tackAngle))) }

        panel.add(heading("Boat"))
        panel.add(row("Speed (m/s)", penSpeed))
        penSpeed.addChangeListener { if (!loading) changed(workbench.withPenSpeed(penSpeed.value as Double)) }

        panel.add(heading("Search"))
        panel.add(row("Squares", cellSize))
        cellSize.addActionListener { if (!loading) replan(workbench.withCellSize(CELL_SIZES[cellSize.selectedIndex])) }
        panel.add(row("Winds tried", runs))
        runs.addChangeListener { if (!loading) replan(workbench.withRuns(value(runs))) }

        panel.add(heading("Course"))
        panel.add(button("Plan route") { plan() })
        panel.add(button("Fit to course") { canvas.fit() })
        panel.add(button("Undo (Ctrl+Z)") { changed(workbench.undo()); plan() })
        panel.add(button("Clear track") { replan(workbench.clearTrack()) })
        panel.add(button("Clear everything") { replan(workbench.clearAll()) })
        panel.add(button("Open…") { open() })
        panel.add(button("Save…") { save() })
        panel.add(Box.createVerticalGlue())
        return panel
    }

    private fun bindShortcuts() {
        val root = rootPane
        root.registerKeyboardAction(
            { changed(workbench.undo()); plan() },
            KeyStroke.getKeyStroke(KeyEvent.VK_Z, toolkit.menuShortcutKeyMaskEx),
            JComponent.WHEN_IN_FOCUSED_WINDOW,
        )
        root.registerKeyboardAction(
            { canvas.fit() },
            KeyStroke.getKeyStroke(KeyEvent.VK_F, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW,
        )
        root.registerKeyboardAction(
            { plan() },
            KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW,
        )
    }

    // --- what the window does --------------------------------------------------------------------

    /** The workbench has changed and the chart should show it; the route is not asked for again. */
    private fun changed(updated: Workbench) {
        workbench = updated
        canvas.show(updated)
        showReport()
    }

    private fun replan(updated: Workbench) {
        changed(updated)
        plan()
    }

    /** Asks the strategy about the course as it stands, off the event thread. */
    private fun plan() {
        val sketch = workbench.sketch
        val generation = asked.incrementAndGet()
        planner.execute {
            val analysis = SketchAnalysis.of(sketch)
            SwingUtilities.invokeLater {
                // A drag that outran the planner: this answer is about a course that no longer exists.
                if (asked.get() == generation) changed(workbench.planned(analysis))
            }
        }
    }

    private fun showReport() {
        val story = Report.of(workbench.analysis)
        report.text = (listOf(story.headline) + story.lines).joinToString("\n")
        report.caretPosition = 0
    }

    private fun open() {
        val file = choose(open = true) ?: return
        try {
            val sketch = CourseSketchFormat.read(file.readText())
            loading = true
            windDirection.value = sketch.wind.directionDegrees
            tackAngle.value = sketch.wind.tackAngleDegrees
            runs.value = sketch.settings.course.raceLine.runs
            cellSize.selectedIndex = CELL_SIZES.indexOf(sketch.settings.course.grid.cellSizeMeters).coerceAtLeast(0)
            loading = false
            changed(workbench.opened(sketch))
            canvas.fit()
            plan()
        } catch (failure: Exception) {
            loading = false
            JOptionPane.showMessageDialog(this, failure.message, "That file is not a course", JOptionPane.WARNING_MESSAGE)
        }
    }

    private fun save() {
        val chosen = choose(open = false) ?: return
        val file = if (chosen.extension.isEmpty()) File("${chosen.path}.$EXTENSION") else chosen
        try {
            file.writeText(CourseSketchFormat.write(workbench.sketch.copy(name = file.nameWithoutExtension)))
        } catch (failure: Exception) {
            JOptionPane.showMessageDialog(this, failure.message, "The course could not be saved", JOptionPane.WARNING_MESSAGE)
        }
    }

    private fun choose(open: Boolean): File? {
        val chooser = JFileChooser().apply {
            fileFilter = FileNameExtensionFilter("Drawn courses (*.$EXTENSION)", EXTENSION)
        }
        val answer = if (open) chooser.showOpenDialog(this) else chooser.showSaveDialog(this)
        return if (answer == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

    // --- small Swing helpers ---------------------------------------------------------------------

    private fun heading(text: String) = JLabel(text).apply {
        foreground = Palette.FAINT_TEXT
        alignmentX = Component.LEFT_ALIGNMENT
        border = EmptyBorder(12, 0, 4, 0)
    }

    private fun row(label: String, field: JComponent) = JPanel().apply {
        layout = BorderLayout(8, 0)
        background = Palette.PANEL
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, 30)
        add(JLabel(label).apply { foreground = Palette.TEXT }, BorderLayout.WEST)
        add(field, BorderLayout.EAST)
    }

    private fun button(text: String, action: () -> Unit) = JButton(text).apply {
        alignmentX = Component.LEFT_ALIGNMENT
        maximumSize = Dimension(Int.MAX_VALUE, 30)
        addActionListener { action() }
    }

    private fun spinner(value: Int, from: Int, to: Int, step: Int) = JSpinner(SpinnerNumberModel(value, from, to, step))

    private fun value(spinner: JSpinner) = (spinner.value as Number).toInt()

    private companion object {
        const val EXTENSION = "course"

        /** The square sizes the workbench offers, in metres; null lets the racing area choose its own. */
        val CELL_SIZES: List<Double?> = listOf(null, 5.0, 10.0, 20.0, 30.0, 50.0, 100.0)
    }
}
