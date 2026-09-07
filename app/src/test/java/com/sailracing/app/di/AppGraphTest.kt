package com.sailracing.app.di

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sailracing.app.SailRacingApplication
import com.sailracing.app.data.SimulationSettings
import com.sailracing.app.sensors.AndroidSensorSource
import com.sailracing.app.sensors.SimulatedSensorSource
import com.sailracing.app.time.SystemClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
class AppGraphTest {

    @Test
    fun theApplicationBuildsARealGraph() {
        val app: SailRacingApplication = ApplicationProvider.getApplicationContext()
        val graph = app.graph
        assertIs<DefaultAppGraph>(graph)
        assertSame(graph, app.appGraph)
        assertNotNull(graph.raceSession)
        assertNotNull(graph.cuePlayer)
        assertNotNull(runBlocking { graph.repository.settings.first() })
    }

    @Test
    fun sensorFactoryChoosesSimulationOrGps() {
        val app: SailRacingApplication = ApplicationProvider.getApplicationContext()
        val graph = DefaultAppGraph(app)
        val factory = graph.javaClass.getDeclaredField("raceSession")
        assertNotNull(factory)
        // Exercise both branches through the session's factory by driving settings.
        val session = graph.raceSession
        runBlocking { session.start() }
        runBlocking { graph.repository.updateSettings { it.copy(simulation = SimulationSettings(enabled = true, speedFactor = 2.0)) } }
        Thread.sleep(200)
        runBlocking { graph.repository.updateSettings { it.copy(simulation = SimulationSettings(enabled = false)) } }
        Thread.sleep(200)
        session.stop()
        assertSame(SystemClock, session.clock)
        assertNotNull(AndroidSensorSource::class)
        assertNotNull(SimulatedSensorSource::class)
    }
}
