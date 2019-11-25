/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils.logging

import io.ktor.util.logging.rolling.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.test.*

class TotalCountTriggerTest : CoroutineScope {
    private val job = Job()
    private val fileSystem = TestFileSystem()

    override val coroutineContext: CoroutineContext get() = job

    @AfterTest
    fun cleanup() {
        job.cancel("Test completed.")
    }

    @Test
    fun totalCountSmokeTest() {
        repeat(3) {
            fileSystem.addFile("a-$it")

            var triggered = false
            val trigger =
                Trigger.TotalCount(
                    2, fileSystem, FilePathPattern(
                        listOf(
                            FilePathPattern.Component.ConstantPart("a-"),
                            FilePathPattern.Component.Number
                        )
                    ),
                    { triggered = true },
                    { fail() }
                )

            val expected = fileSystem.allFiles.size > 2
            assertEquals(expected, trigger.check(), "Trigger should trigger for ${fileSystem.allFiles.size} files")
            assertEquals(triggered, expected, "Notification should be delivered")
        }
    }

    @Test
    fun totalCountOtherFiles() {
        fileSystem.addFile("a-1")
        fileSystem.addFile("a-2")
        fileSystem.addFile("b-2")

        val trigger =
            Trigger.TotalCount(
                2, fileSystem, FilePathPattern("a-%i"),
                { fail() },
                { fail() }
            )

        assertEquals(false, trigger.check(), "Trigger shouldn't trigger for irrelevant files")
    }

    @Test
    fun testDirectoryPattern() {
        fileSystem.addFile("logs-dir-1/log-1.log")
        fileSystem.addFile("logs-dir-1/log-2.log")
        fileSystem.addFile("logs-dir-2/log-3.log")
        fileSystem.addFile("other-dir/log-3.log")

        val trigger =
            Trigger.TotalCount(
                3, fileSystem, FilePathPattern("logs-dir-%i/log-%i.log"),
                { fail() },
                { fail() }
            )

        assertEquals(false, trigger.check(), "Trigger shouldn't trigger for irrelevant files")
    }

    @Test
    fun testNotifications() {
        var notified = 0
        val trigger =
            Trigger.TotalCount(
                3, fileSystem, FilePathPattern("any%i"),
                { notified++ },
                { fail() }
            )

        trigger.setup(this)

        assertFalse { trigger.check() }

        fileSystem.addFile("any0")
        fileSystem.addFile("any1")

        assertEquals(0, notified)

        fileSystem.addFile("any2")
        assertEquals(0, notified)

        fileSystem.addFile("any3")
        assertEquals(1, notified)
    }

    @Test
    fun testNotificationsWithRemoved() {
        var notified = 0
        val trigger =
            Trigger.TotalCount(
                3, fileSystem, FilePathPattern("any%i"),
                { notified++ },
                { fail() }
            )

        trigger.setup(this)

        assertFalse { trigger.check() }

        fileSystem.addFile("any0")
        fileSystem.addFile("any1")

        assertEquals(0, notified)

        fileSystem.addFile("any2")
        assertEquals(0, notified)

        fileSystem.delete("any0")
        fileSystem.addFile("any0")
        assertEquals(0, notified)

        fileSystem.addFile("any3")
        assertEquals(1, notified)
    }

}
