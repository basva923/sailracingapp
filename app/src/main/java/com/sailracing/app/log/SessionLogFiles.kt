package com.sailracing.app.log

import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Where the session logs live and how many of them are kept: one file per session, named after the moment
 * it started, in a folder the phone hands out for the app's own files. On a real phone that folder is
 * `Android/data/<package>/files/sessions`, which is reachable over USB (`adb pull`) and from a file
 * manager without root - the point of the log is that it can be taken off the boat and read.
 *
 * Old logs are dropped when there are too many of them or they take up too much room, oldest first, so a
 * season of racing cannot fill a phone.
 */
class SessionLogFiles(
    val directory: File,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
    private val maxTotalBytes: Long = DEFAULT_MAX_TOTAL_BYTES,
) {

    /** The logs there are, newest first. */
    fun list(): List<File> =
        directory.listFiles { file -> file.isFile && file.name.endsWith(EXTENSION) }
            ?.sortedByDescending { it.name }
            .orEmpty()

    fun totalBytes(): Long = list().sumOf { it.length() }

    /** "7 sessions · 12.4 MB", or that there is nothing to show yet. */
    fun summary(): String {
        val files = list()
        if (files.isEmpty()) return "No sessions logged yet"
        val megabytes = totalBytes() / (1024.0 * 1024.0)
        val sessions = if (files.size == 1) "1 session" else "${files.size} sessions"
        return String.format(Locale.ROOT, "%s · %.1f MB", sessions, megabytes)
    }

    fun deleteAll(): Int = list().count { it.delete() }

    /**
     * A new, empty log file for a session starting at [startedAtMillis], with room made for it first.
     * Null when the folder cannot be made or written to, which is not worth failing a race over.
     */
    fun create(startedAtMillis: Long): File? {
        if (!directory.isDirectory && !directory.mkdirs()) return null
        prune()
        val stamp = NAME_FORMAT.format(Instant.ofEpochMilli(startedAtMillis))
        var file = File(directory, "session-$stamp$EXTENSION")
        var attempt = 1
        while (file.exists()) file = File(directory, "session-$stamp-${attempt++}$EXTENSION")
        return runCatching { file.takeIf { it.createNewFile() } }.getOrNull()
    }

    /** Drops the oldest logs until a new one fits within both limits. */
    private fun prune() {
        val files = list().toMutableList()
        var total = files.sumOf { it.length() }
        while (files.isNotEmpty() && (files.size >= maxFiles || total > maxTotalBytes)) {
            val oldest = files.removeAt(files.size - 1)
            total -= oldest.length()
            oldest.delete()
        }
    }

    companion object {
        const val EXTENSION: String = ".jsonl"

        /** A season of racing, and no more: the oldest is dropped when the next session starts. */
        const val DEFAULT_MAX_FILES: Int = 30

        /** About an hour of racing is a couple of megabytes, so this is a lot of racing. */
        const val DEFAULT_MAX_TOTAL_BYTES: Long = 256L * 1024 * 1024

        /** UTC, so that logs sort by name wherever the boat is and whatever the phone thinks the time is. */
        private val NAME_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss'Z'", Locale.ROOT).withZone(ZoneOffset.UTC)
    }
}
