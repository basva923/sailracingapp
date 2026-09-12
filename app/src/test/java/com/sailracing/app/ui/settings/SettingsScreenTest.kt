package com.sailracing.app.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.robolectric.annotation.Config
import com.sailracing.app.data.AppSettings
import com.sailracing.app.ui.theme.SailRacingTheme
import com.sailracing.domain.race.ApproachSpeed
import com.sailracing.domain.race.HeadingSource
import com.sailracing.domain.timer.CuePolicy
import com.sailracing.simulation.SimulationCatalog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
class SettingsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private var settings by mutableStateOf(AppSettings())
    private var deletedLogs = 0
    private val actions = SettingsActions(
        update = { transform -> settings = transform(settings) },
        deleteLogs = { deletedLogs++ },
    )

    private fun show() {
        compose.setContent {
            SailRacingTheme {
                SettingsScreen(
                    settings = settings,
                    actions = actions,
                    versionName = "1.0",
                    logSummary = "3 sessions · 1.2 MB",
                    logLocation = "/sdcard/Android/data/com.sailracing.app/files/sessions",
                )
            }
        }
    }

    @Test
    fun switchesTransformTheSettings() {
        show()
        compose.onNodeWithTag("manualApproach").performScrollTo().performClick()
        assertIs<ApproachSpeed.Manual>(settings.race.approachSpeed)
        compose.onNodeWithTag("manualApproach").performClick()
        assertIs<ApproachSpeed.AverageUpwindVmg>(settings.race.approachSpeed)
        compose.onNodeWithTag("beeps").performScrollTo().performClick()
        assertEquals(false, settings.race.cuePolicy.enabled)
        compose.onNodeWithTag("vibrate").performScrollTo().performClick()
        assertEquals(false, settings.vibrate)
        compose.onNodeWithTag("phoneReversed").performScrollTo().performClick()
        assertEquals(180, settings.race.compassOffsetDegrees)
        compose.onNodeWithTag("keepScreenOn").performScrollTo().performClick()
        assertEquals(false, settings.keepScreenOn)
        compose.onNodeWithTag("simulationEnabled").performScrollTo().performClick()
        assertEquals(true, settings.simulation.enabled)
        compose.onNodeWithTag("simulationAutoPlay").performScrollTo().performClick()
        assertEquals(false, settings.simulation.autoPlayActions)
        compose.onNodeWithTag("logSessions").performScrollTo().performClick()
        assertEquals(false, settings.logSessions)
    }

    @Test
    fun theSquareSizeIsAutomaticUntilTheSailorChoosesOne() {
        show()
        compose.onNodeWithTag("cellSize").performScrollTo().assertTextEquals("Square size", "Automatic")
        // Switching it on starts from the default, and the dialog steps it in fives of metres.
        compose.onNodeWithTag("chooseCellSize").performScrollTo().performClick()
        assertEquals(50.0, settings.race.course.grid.cellSizeMeters)
        compose.onNodeWithTag("cellSize").performScrollTo().assertTextEquals("Square size", "50 m")
        compose.onNodeWithTag("cellSize").performClick()
        compose.onNodeWithTag("stepUp").performClick()
        compose.onNodeWithTag("numberValue").assertTextEquals("55 m")
        compose.onNodeWithTag("stepUpBig").performClick()
        compose.onNodeWithTag("numberValue").assertTextEquals("105 m")
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(105.0, settings.race.course.grid.cellSizeMeters)
        // The smallest square there is: half a boat length.
        compose.onNodeWithTag("cellSize").performScrollTo().performClick()
        repeat(2) { compose.onNodeWithTag("stepDownBig").performClick() }
        compose.onNodeWithTag("numberValue").assertTextEquals("5 m")
        compose.onNodeWithTag("stepDown").performClick()
        compose.onNodeWithTag("numberValue").assertTextEquals("5 m")
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(5.0, settings.race.course.grid.cellSizeMeters)
        // And switching it off gives the racing area its choice back.
        compose.onNodeWithTag("chooseCellSize").performScrollTo().performClick()
        assertEquals(null, settings.race.course.grid.cellSizeMeters)
    }

    @Test
    fun theLogsCanBeSeenAndDeleted() {
        show()
        compose.onNodeWithTag("deleteLogs").performScrollTo().assertTextEquals("Delete the logs", "3 sessions · 1.2 MB")
        compose.onNodeWithTag("logLocation").performScrollTo()
            .assertTextEquals("/sdcard/Android/data/com.sailracing.app/files/sessions")
        compose.onNodeWithTag("deleteLogs").performClick()
        compose.onNodeWithText("Delete the logs?").assertIsDisplayed()
        compose.onNodeWithTag("cancel").performClick()
        assertEquals(0, deletedLogs)
        compose.onNodeWithTag("deleteLogs").performScrollTo().performClick()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(1, deletedLogs)
    }

    @Test
    fun valueRowsOpenDialogs() {
        settings = AppSettings(race = AppSettings().race.copy(approachSpeed = ApproachSpeed.Manual(2.0)))
        show()
        // 2 m/s = 3.9 kn; +1 kn -> 4.9 kn.
        compose.onNodeWithTag("approachSpeed").performScrollTo().performClick()
        compose.onNodeWithTag("stepUpBig").performClick()
        compose.onNodeWithTag("numberValue").assertTextEquals("4.9 kn")
        compose.onNodeWithTag("stepUp").performClick()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(5.0, (settings.race.approachSpeed as ApproachSpeed.Manual).speedMps * 1.943844, 0.01)

        compose.onNodeWithTag("tenSecondWindow").performScrollTo().performClick()
        compose.onNodeWithTag("stepDownBig").performClick()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(60, settings.race.cuePolicy.tenSecondWindowSeconds)

        compose.onNodeWithTag("secondWindow").performScrollTo().performClick()
        compose.onNodeWithTag("stepDownBig").performClick()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(5, settings.race.cuePolicy.secondWindowSeconds)

        compose.onNodeWithTag("closeHauledBand").performScrollTo().performClick()
        compose.onNodeWithTag("stepUpBig").performClick()
        compose.onNodeWithTag("stepUpBig").performClick()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(30, settings.race.closeHauledBandDegrees)

        compose.onNodeWithTag("simulationSpeed").performScrollTo().performClick()
        compose.onNodeWithTag("stepUpBig").performClick()
        compose.onNodeWithTag("stepUp").performClick()
        compose.onNodeWithTag("stepUpBig").performClick()
        compose.onNodeWithTag("stepDown").performClick()
        compose.onNodeWithTag("stepDown").performClick()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(10.0, settings.simulation.speedFactor)

        compose.onNodeWithTag("simulationSpeed").performScrollTo().performClick()
        compose.onNodeWithTag("cancel").performClick()
    }

    @Test
    fun fallbackSpeedDialogKeepsAutomaticMode() {
        show()
        // Default fallback 1.5 m/s = 2.9 kn; +1 kn -> 3.9 kn.
        compose.onNodeWithTag("approachSpeed").performScrollTo().performClick()
        compose.onNodeWithTag("stepUpBig").performClick()
        compose.onNodeWithTag("confirm").performClick()
        val approach = assertIs<ApproachSpeed.AverageUpwindVmg>(settings.race.approachSpeed)
        assertEquals(3.9, approach.fallbackMps * 1.943844, 0.01)
    }

    @Test
    fun oneOfTheSimulationsCanBeLoaded() {
        show()
        // The row says which one is loaded now; the dialog offers every one of them.
        compose.onNodeWithTag("simulationScenario").performScrollTo()
            .assertTextContains(SimulationCatalog.default.title)
        compose.onNodeWithTag("simulationScenario").performClick()
        SimulationCatalog.all.forEach { compose.onNodeWithTag("choice_" + it.id).performScrollTo().assertIsDisplayed() }
        compose.onNodeWithTag("choice_oscillating").performScrollTo().performClick()
        assertEquals("oscillating", settings.simulation.scenarioId)
        assertEquals("Oscillating breeze, 10 kn from 020°, ±10° every 6 minutes", settings.simulation.scenario.title)
        compose.onNodeWithTag("simulationScenario").performScrollTo()
            .assertTextContains(settings.simulation.scenario.title)
        // Closing the dialog without choosing keeps the one that is loaded.
        compose.onNodeWithTag("simulationScenario").performClick()
        compose.onNodeWithTag("cancel").performClick()
        assertEquals("oscillating", settings.simulation.scenarioId)
    }

    @Test
    fun theHeadingCanBeTakenFromTheCompassOrTheGps() {
        show()
        // The row says which one is in use and what it costs; the dialog offers both.
        compose.onNodeWithTag("headingSource").performScrollTo().assertTextContains("GPS course")
        compose.onNodeWithTag("headingSource").performClick()
        compose.onNodeWithTag("choice_COMPASS").performScrollTo().performClick()
        assertEquals(HeadingSource.COMPASS, settings.race.headingSource)
        compose.onNodeWithTag("headingSource").performScrollTo().assertTextContains("Compass")

        // Closing the dialog without choosing keeps the one in use.
        compose.onNodeWithTag("headingSource").performClick()
        compose.onNodeWithTag("cancel").performClick()
        assertEquals(HeadingSource.COMPASS, settings.race.headingSource)
        compose.onNodeWithTag("headingSource").performClick()
        compose.onNodeWithTag("choice_COURSE_OVER_GROUND").performScrollTo().performClick()
        assertEquals(HeadingSource.COURSE_OVER_GROUND, settings.race.headingSource)
    }

    @Test
    fun aboutTextAndCuePolicyDescription() {
        show()
        compose.onNodeWithTag("about").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(
            "Sessions are started, ended and cleared on the Session screen. The racing area on the map follows the track " +
                "around the line, the mark and the boat. Sail Racing 1.0",
        ).assertIsDisplayed()
        assertTrue(CuePolicy().describe().startsWith("on"))
        assertEquals("off", CuePolicy(enabled = false).describe())
    }
}
