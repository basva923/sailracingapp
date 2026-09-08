package com.sailracing.app.log

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** One file per session, named after the moment it started, and never more of them than fit on a phone. */
class SessionLogFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun files(maxFiles: Int = 30, maxTotalBytes: Long = 1L shl 40) =
        SessionLogFiles(File(folder.root, "sessions"), maxFiles, maxTotalBytes)

    @Test
    fun aSessionGetsAFileNamedAfterTheMomentItStarted() {
        val logs = files()
        assertEquals("No sessions logged yet", logs.summary())
        val file = assertNotNull(logs.create(1_788_876_930_000L))
        assertEquals("session-20260908-141530Z.jsonl", file.name)
        assertTrue(file.exists())
        // A second session in the same second still gets a file of its own.
        assertEquals("session-20260908-141530Z-1.jsonl", assertNotNull(logs.create(1_788_876_930_000L)).name)
        assertEquals(2, logs.list().size)
        // Newest first, whatever order the folder hands them over in.
        assertEquals("session-20260908-141531Z.jsonl", assertNotNull(logs.create(1_788_876_931_000L)).name)
        assertEquals("session-20260908-141531Z.jsonl", logs.list().first().name)
        assertTrue(logs.summary().startsWith("3 sessions · "), logs.summary())
    }

    @Test
    fun theOldestAreDroppedWhenThereAreTooManyOfThem() {
        val logs = files(maxFiles = 3)
        val names = (1..5).map { assertNotNull(logs.create(1_788_876_930_000L + it * 1_000L)).name }
        assertEquals(3, logs.list().size)
        assertEquals(names.takeLast(3).reversed(), logs.list().map { it.name })
    }

    @Test
    fun andWhenTheyTakeUpTooMuchRoom() {
        val logs = files(maxTotalBytes = 100L)
        assertNotNull(logs.create(1_788_876_930_000L)).writeText("x".repeat(200))
        val kept = assertNotNull(logs.create(1_788_876_931_000L))
        assertEquals(listOf(kept.name), logs.list().map { it.name })
        assertEquals("1 session · 0.0 MB", logs.summary())
        assertEquals(0L, logs.totalBytes())
        assertEquals(1, logs.deleteAll())
        assertTrue(logs.list().isEmpty())
    }

    @Test
    fun aFolderThatCannotBeMadeIsNotWorthFailingARaceOver() {
        val blocked = File(folder.newFile("in-the-way"), "sessions")
        assertNull(SessionLogFiles(blocked).create(0L))
        assertEquals("No sessions logged yet", SessionLogFiles(blocked).summary())
        assertEquals(0, SessionLogFiles(blocked).deleteAll())
    }
}
