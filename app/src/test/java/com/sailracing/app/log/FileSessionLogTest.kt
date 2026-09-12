package com.sailracing.app.log

import com.sailracing.app.data.AppSettings
import com.sailracing.domain.race.RaceEngine
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.timer.Cue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The black box itself: lines out of the engine, a file per session, and nothing blocking the race. */
@OptIn(ExperimentalCoroutinesApi::class)
class FileSessionLogTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val engine = RaceEngine()

    private fun TestScopeLog(scope: kotlinx.coroutines.CoroutineScope, enabled: () -> Boolean = { true }): FileSessionLog =
        FileSessionLog(
            files = SessionLogFiles(File(folder.root, "sessions")),
            scope = scope,
            enabled = enabled,
            dispatcher = StandardTestDispatcher(scope.coroutineContext[kotlinx.coroutines.test.TestCoroutineScheduler]),
        )

    private fun lines(): List<String> {
        val file = File(folder.root, "sessions").listFiles()?.singleOrNull() ?: return emptyList()
        return file.readLines()
    }

    @Test
    fun aSessionIsWrittenAsItHappens() = runTest {
        val log = TestScopeLog(this)
        log.start(1_000L, "1.0.0", AppSettings())
        log.record(RaceEvent.SetWindDirection(210), engine.snapshot(1_000L))
        log.cue(Cue.START, 1_500L)
        log.end(2_000L)
        advanceUntilIdle()
        log.awaitWritten()

        val written = lines()
        assertEquals("session", written.first().type())
        assertEquals(listOf("session", "event", "state", "cue", "end"), written.map { it.type() })
        assertTrue(written[1].contains(""""degrees":210"""), written[1])
        assertEquals("$folder/sessions".let { File(folder.root, "sessions").absolutePath }, log.location)
        assertTrue(log.summary().startsWith("1 session"))
    }

    @Test
    fun switchingLoggingOnHalfwayThroughStillGetsAFileWithItsHead() = runTest {
        var logging = false
        val log = TestScopeLog(this) { logging }
        log.start(1_000L, "1.0.0", AppSettings())
        log.record(RaceEvent.SetWindDirection(210), engine.snapshot(1_000L))
        advanceUntilIdle()
        assertTrue(lines().isEmpty(), "nothing should be written while logging is off")

        logging = true
        log.record(RaceEvent.SetTackAngle(43), engine.snapshot(2_000L))
        log.end(3_000L)
        advanceUntilIdle()
        log.awaitWritten()
        val written = lines()
        assertEquals(listOf("session", "event", "state", "end"), written.map { it.type() })
        assertTrue(written[1].contains(""""degrees":43"""), written[1])
    }

    @Test
    fun everySessionGetsItsOwnFile() = runTest {
        val log = TestScopeLog(this)
        log.start(1_000L, "1.0.0", AppSettings())
        log.end(2_000L)
        advanceUntilIdle()
        log.awaitWritten()
        log.start(60_000L, "1.0.0", AppSettings())
        log.end(61_000L)
        advanceUntilIdle()
        log.awaitWritten()
        assertEquals(2, File(folder.root, "sessions").listFiles()?.size)
        assertTrue(log.summary().startsWith("2 sessions"))

        log.deleteAll()
        assertEquals(0, File(folder.root, "sessions").listFiles()?.size)
    }

    /**
     * What happens between sessions - the sailor clearing the session, resetting the statistics - belongs
     * to no session, and a file opened for it would carry the head of the session that had just ended.
     */
    @Test
    fun nothingIsWrittenBetweenSessions() = runTest {
        val log = TestScopeLog(this)
        log.start(1_000L, "1.0.0", AppSettings())
        log.end(2_000L)
        advanceUntilIdle()
        log.awaitWritten()
        log.record(RaceEvent.ClearSession, engine.snapshot(3_000L))
        log.cue(Cue.START, 3_500L)
        advanceUntilIdle()
        assertEquals(1, File(folder.root, "sessions").listFiles()?.size)
        assertEquals(listOf("session", "end"), lines().map { it.type() })

        log.start(60_000L, "1.0.0", AppSettings())
        log.record(RaceEvent.SetWindDirection(210), engine.snapshot(60_000L))
        log.end(61_000L)
        advanceUntilIdle()
        log.awaitWritten()
        val files = File(folder.root, "sessions").listFiles()!!.sortedBy { it.name }
        assertEquals(2, files.size)
        assertEquals(listOf("session", "event", "state", "end"), files[1].readLines().map { it.type() })
    }

    @Test
    fun aFolderItCannotWriteToIsNotWorthFailingARaceOver() = runTest {
        val log = FileSessionLog(
            files = SessionLogFiles(File(folder.newFile("in-the-way"), "sessions")),
            scope = this,
            dispatcher = StandardTestDispatcher(testScheduler),
        )
        log.start(1_000L, "1.0.0", AppSettings())
        log.record(RaceEvent.Tick(1_000L), engine.snapshot(1_000L))
        log.end(2_000L)
        advanceUntilIdle()
        log.awaitWritten()
        assertEquals("No sessions logged yet", log.summary())
    }

    /** The type of a log line, without parsing the whole of it. */
    private fun String.type(): String = substringAfter(""""type":"""").substringBefore('"')
}
