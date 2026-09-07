package com.sailracing.app

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sailracing.app.fakes.TestAppGraph
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h914dp-xxhdpi")
class MainActivityTest {

    private val app: SailRacingApplication = ApplicationProvider.getApplicationContext()
    private lateinit var graph: TestAppGraph

    private val compose = createAndroidComposeRule<MainActivity>()

    /** The fake graph must be in place before the compose rule launches the activity (a @Before would be too late). */
    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(
            TestRule { base, _ ->
                object : Statement() {
                    override fun evaluate() {
                        graph = TestAppGraph()
                        app.graph = graph
                        base.evaluate()
                    }
                }
            },
        )
        .around(compose)

    @Test
    fun asksForLocationAndShowsTheAppOnceGranted() {
        // Without the permission the activity shows the explanation and has asked the system.
        compose.onNodeWithText("Allow location").assertIsDisplayed()
        val requested = shadowOf(compose.activity).lastRequestedPermission
        assertNotNull(requested)
        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in requested.requestedPermissions)

        compose.onNodeWithText("Allow location").performClick()

        // Grant and recreate: the real UI appears on the session screen; nothing runs until the sailor starts.
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag("sessionTitle").assertIsDisplayed()
        compose.onNodeWithTag("nav_SETTINGS").assertIsDisplayed()
        assertEquals(null, shadowOf(app).nextStartedService)
        assertTrue(!graph.raceSession.isRunning.value)

        // Starting the session starts the foreground service.
        compose.onNodeWithTag("startSession").performClick()
        graph.scheduler.runCurrent()
        compose.waitForIdle()
        assertTrue(graph.raceSession.isRunning.value)
        val started = shadowOf(app).nextStartedService
        assertNotNull(started)
        assertEquals("com.sailracing.app.race.RaceService", started.component?.className)
    }
}
