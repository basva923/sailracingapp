package com.sailracing.app.log

import com.sailracing.app.data.AppSettings
import com.sailracing.domain.race.RaceEvent
import com.sailracing.domain.race.RaceSnapshot
import com.sailracing.domain.timer.Cue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The session's black box: everything the app was told and everything it made of it, written to a file as
 * it happens, so that a race can be gone through afterwards - or replayed - instead of remembered.
 */
interface SessionLog {

    /** Opens a log for a session. Everything logged until [end] belongs to it. */
    fun start(nowMillis: Long, versionName: String, settings: AppSettings)

    /** One event and what the app made of it. Called on the engine's thread: it must not block. */
    fun record(event: RaceEvent, snapshot: RaceSnapshot)

    fun cue(cue: Cue, nowMillis: Long)

    fun end(nowMillis: Long)

    /** Where the logs are, for the settings screen to point at. */
    val location: String

    /** What is on the phone: "7 sessions · 12.4 MB". */
    suspend fun summary(): String

    suspend fun deleteAll()

    /** A log that keeps nothing, for tests and previews. */
    object None : SessionLog {
        override val location: String = ""
        override fun start(nowMillis: Long, versionName: String, settings: AppSettings) = Unit
        override fun record(event: RaceEvent, snapshot: RaceSnapshot) = Unit
        override fun cue(cue: Cue, nowMillis: Long) = Unit
        override fun end(nowMillis: Long) = Unit
        override suspend fun summary(): String = "Not logging"
        override suspend fun deleteAll() = Unit
    }
}

/**
 * A [SessionLog] that writes JSON lines to a file, one file per session.
 *
 * Recording happens on the caller's thread and is only the making of a line; the file is written by one
 * coroutine draining a queue, so the race engine never waits for a disk. If the queue ever fills up - a
 * card that has gone away, a simulation at fifty times speed - the oldest lines are dropped rather than
 * the boat being held up.
 *
 * @param enabled read whenever something is logged, so switching logging off stops it mid-session and
 *   switching it on starts a file there and then.
 */
class FileSessionLog(
    private val files: SessionLogFiles,
    private val scope: CoroutineScope,
    private val recorder: SessionRecorder = SessionRecorder(),
    private val enabled: () -> Boolean = { true },
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SessionLog {

    /** One open log file and the coroutine feeding it. Closing the queue closes the file, once drained. */
    private class Sink(val lines: Channel<String>, val job: Job)

    @Volatile
    private var sink: Sink? = null

    @Volatile
    private var header: LogRecord? = null

    override val location: String get() = files.directory.absolutePath

    override fun start(nowMillis: Long, versionName: String, settings: AppSettings) {
        val head = recorder.start(nowMillis, versionName, settings)
        header = head
        write(head)
    }

    override fun record(event: RaceEvent, snapshot: RaceSnapshot) {
        if (!enabled()) return
        recorder.record(event, snapshot).forEach(::write)
    }

    override fun cue(cue: Cue, nowMillis: Long) = write(recorder.cue(cue, nowMillis))

    override fun end(nowMillis: Long) {
        write(recorder.end(nowMillis))
        // Closing the queue lets the writer finish what is in it and then close the file.
        sink?.lines?.close()
        sink = null
    }

    override suspend fun summary(): String = withContext(dispatcher) { files.summary() }

    override suspend fun deleteAll() {
        withContext(dispatcher) { files.deleteAll() }
    }

    /** Waits for the file to be written and closed: for tests, and for nothing else. */
    suspend fun awaitWritten() {
        sink?.job?.join()
    }

    private fun write(record: LogRecord) {
        if (!enabled()) return
        val opened = open(record.timeMillis)
        val head = header
        // Every file starts with what the session was started with, even one opened halfway through a
        // session because the sailor switched logging on.
        if (opened.fresh && head != null && head !== record) opened.sink.lines.trySend(head.toJsonLine())
        opened.sink.lines.trySend(record.toJsonLine())
    }

    private fun open(startedAtMillis: Long): Opened {
        sink?.let { return Opened(it, fresh = false) }
        synchronized(this) {
            sink?.let { return Opened(it, fresh = false) }
            val lines = Channel<String>(QUEUE_CAPACITY, BufferOverflow.DROP_OLDEST)
            val job = scope.launch(dispatcher) {
                val file = files.create(startedAtMillis) ?: return@launch
                file.bufferedWriter().use { output ->
                    for (line in lines) {
                        output.write(line)
                        output.newLine()
                        // Flushed as it catches up: a log that is only on disk when the app closes cleanly
                        // is exactly the log that is missing when the app did not.
                        if (lines.isEmpty) output.flush()
                    }
                }
            }
            val fresh = Sink(lines, job)
            sink = fresh
            return Opened(fresh, fresh = true)
        }
    }

    private class Opened(val sink: Sink, val fresh: Boolean)

    private companion object {
        /** A few minutes of racing in hand if the disk stalls; beyond that the oldest lines are dropped. */
        const val QUEUE_CAPACITY = 4096
    }
}
