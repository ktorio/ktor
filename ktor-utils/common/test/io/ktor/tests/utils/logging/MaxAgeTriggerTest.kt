/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils.logging

import io.ktor.util.date.*
import io.ktor.util.logging.rolling.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.test.*

class MaxAgeTriggerTest : CoroutineScope {
    private val maxAge = 100000L
    private var triggered = 0

    private val job = Job()
    private val dispatcher = TestDelayDispatcher(0L)
    private val fileSystem = TestFileSystem()
    private val trigger = Trigger.MaxAge(maxAge, fileSystem, FilePathPattern("file-%i"), {
        triggered++
    }, { now })

    override val coroutineContext: CoroutineContext get() = job + dispatcher

    private var now: GMTDate
        get() = GMTDate(dispatcher.currentTimeMillis)
        set(value) {
            dispatcher.currentTimeMillis = value.timestamp
        }

    @BeforeTest
    fun setup() {
        trigger.setup(this)
        assertFalse(trigger.check())
    }

    @AfterTest
    fun cleanup() {
        job.cancel("Test completed.")
    }

    @Test
    fun smokeTest() {
        fileSystem.addFile("file-1")
        val file = fileSystem.allFiles.single { it.path == "file-1" } as TestFileSystem.Entry.File

        file.lastModified = now
        fileSystem.notify { it.fileUpdated("file-1") }
        assertEquals(0, triggered)

        file.lastModified = now - 10
        fileSystem.notify { it.fileUpdated("file-1") }
        assertEquals(0, triggered)

        assertEquals(1, dispatcher.delayedCount)

        file.lastModified = now - maxAge - 1
        fileSystem.notify { it.fileUpdated("file-1") }
        assertEquals(1, triggered)
    }

    @Test
    fun testSchedule() {
        fileSystem.addFile("file-1")
        val file = fileSystem.allFiles.single { it.path == "file-1" } as TestFileSystem.Entry.File

        file.lastModified = now
        fileSystem.notify { it.fileUpdated("file-1") }
        assertEquals(0, triggered)

        assertEquals(1, dispatcher.delayedCount)
    }
}
