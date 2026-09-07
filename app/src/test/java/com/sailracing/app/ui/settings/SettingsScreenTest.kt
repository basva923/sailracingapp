package com.sailracing.app.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import com.sailracing.domain.timer.CuePolicy
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
    private val log = mutableListOf<String>()
    private val actions = SettingsActions(
        update = { transform -> settings = transform(settings) },
        resetWindStatistics = { log += "resetWind" },
        resetSpeedStatistics = { log += "resetSpeed" },
        endSession = { log += "end" },
    )

    private fun show() {
        compose.setContent { SailRacingTheme { SettingsScreen(settings = settings, actions = actions, versionName = "1.0") } }
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

        compose.onNodeWithTag("upwindMaxTwa").performScrollTo().performClick()
        compose.onNodeWithTag("stepUpBig").performClick()
        compose.onNodeWithTag("stepUpBig").performClick()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(70, settings.race.upwindMaxTwaDegrees)

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
    fun sessionActionsAskForConfirmation() {
        show()
        compose.onNodeWithTag("resetStatistics").performScrollTo().performClick()
        compose.onNodeWithText("Reset all statistics?").assertIsDisplayed()
        compose.onNodeWithTag("confirm").performClick()
        compose.onNodeWithTag("endSession").performScrollTo().performClick()
        compose.onNodeWithTag("cancel").performClick()
        compose.onNodeWithTag("endSession").performClick()
        compose.onNodeWithTag("confirm").performClick()
        assertEquals(listOf("resetWind", "resetSpeed", "end"), log)
        assertTrue(CuePolicy().describe().startsWith("on"))
        assertEquals("off", CuePolicy(enabled = false).describe())
    }
}
