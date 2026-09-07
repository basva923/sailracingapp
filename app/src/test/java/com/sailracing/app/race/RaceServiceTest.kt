package com.sailracing.app.race

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sailracing.app.SailRacingApplication
import com.sailracing.app.fakes.TestAppGraph
import com.sailracing.domain.geo.GeoPoint
import com.sailracing.domain.model.PositionFix
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.race.RaceEngine
import com.sailracing.domain.race.RaceState
import com.sailracing.domain.startline.StartLine
import com.sailracing.domain.timer.TimerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RaceServiceTest {

    private val app: SailRacingApplication = ApplicationProvider.getApplicationContext()
    private lateinit var graph: TestAppGraph

    @Before
    fun installGraph() {
        graph = TestAppGraph()
        app.graph = graph
    }

    @Test
    fun notificationTextFollowsThePhase() {
        val idle = RaceEngine().snapshot(0)
        assertEquals(RaceService.NotificationText("Sail Racing", "GPS on - preparing the race"), RaceService.NotificationText.from(idle))

        val engine = RaceEngine(RaceState(startLine = StartLine(GeoPoint(51.14, 5.83), GeoPoint(51.141, 5.83))))
        engine.dispatch(RaceEvent.StartCountdown(1, 0))
        val countdown = RaceService.NotificationText.from(engine.snapshot(10_000))
        assertEquals("Start in 00:50", countdown.title)
        assertEquals("Line not set", countdown.body)
        engine.dispatch(RaceEvent.FixReceived(PositionFix(GeoPoint(51.1405, 5.83), 10_000, 3.0, 0.0, null)))
        assertTrue(RaceService.NotificationText.from(engine.snapshot(10_000)).body.startsWith("Distance to line"))

        val racing = RaceService.NotificationText.from(engine.snapshot(70_000))
        assertEquals("Racing +00:10", racing.title)
        assertEquals("Speed 5.8 kn", racing.body)
        engine.dispatch(RaceEvent.SetTimer(TimerState.Running(0)))
        val noGps = RaceService.NotificationText.from(RaceEngine(RaceState(timer = TimerState.Running(0))).snapshot(5_000))
        assertEquals("Waiting for GPS", noGps.body)
    }

    @Test
    fun runsInTheForegroundWhileTheSessionRunsAndStopsWithIt() {
        graph.startSession()
        assertTrue(graph.raceSession.isRunning.value)

        val controller = Robolectric.buildService(RaceService::class.java, Intent(app, RaceService::class.java))
        val service = controller.create().startCommand(0, 1).get()
        ShadowLooper.idleMainLooper()

        val shadowService = shadowOf(service)
        assertNotNull(shadowService.lastForegroundNotification)
        assertEquals(RaceService.NOTIFICATION_ID, shadowService.lastForegroundNotificationId)
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertNotNull(manager.getNotificationChannel(RaceService.CHANNEL_ID))
        assertFalse(shadowService.isStoppedBySelf)

        // A countdown changes the notification text.
        graph.raceSession.startCountdown(5)
        graph.scheduler.runCurrent()
        ShadowLooper.idleMainLooper()
        assertTrue(shadowOf(manager).allNotifications.isNotEmpty())

        graph.raceSession.stop()
        ShadowLooper.idleMainLooper()
        assertTrue(shadowService.isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun stopActionEndsTheSession() {
        graph.startSession()
        val controller = Robolectric.buildService(RaceService::class.java, Intent(app, RaceService::class.java).setAction(RaceService.ACTION_STOP))
        val service = controller.create().startCommand(0, 1).get()
        assertFalse(graph.raceSession.isRunning.value)
        assertTrue(shadowOf(service).isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun helpersStartAndStopTheService() {
        RaceService.start(app)
        val started = shadowOf(app).nextStartedService
        assertEquals(RaceService::class.java.name, started.component?.className)
        RaceService.stop(app)
        val stopped = shadowOf(app).nextStartedService
        assertEquals(RaceService.ACTION_STOP, stopped.action)
    }
}
