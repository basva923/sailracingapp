package com.sailracing.app.race

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sailracing.app.SailRacingApplication
import com.sailracing.app.fakes.TestAppGraph
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Older Android versions start the foreground service without a service type. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [28])
class RaceServiceLegacyTest {

    @Test
    fun startsInTheForegroundOnAndroid9() {
        val app: SailRacingApplication = ApplicationProvider.getApplicationContext()
        val graph = TestAppGraph()
        app.graph = graph
        graph.startSession()
        val controller = Robolectric.buildService(RaceService::class.java, Intent(app, RaceService::class.java))
        val service = controller.create().startCommand(0, 1).get()
        assertNotNull(shadowOf(service).lastForegroundNotification)
        assertNull(service.onBind(null))
        graph.raceSession.stop()
        controller.destroy()
    }
}
