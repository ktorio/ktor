/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils.logging

import io.ktor.util.logging.rolling.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.test.*

class TotalSizeTriggerTest : CoroutineScope {
    private var triggered = 0

    private val job = Job()
    private val fileSystem = TestFileSystem()
    private val trigger = Trigger.TotalSize(10, fileSystem, FilePathPattern("file-%i"), {
        triggered++
    }, {
        fail("Time source shouldn't be touched")
    })

    override val coroutineContext: CoroutineContext get() = job

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
        fileSystem.addFile("file-1", byteArrayOf(1, 2, 3))
        assertEquals(0, triggered)
        fileSystem.addFile("file-2", byteArrayOf(1, 2, 3))
        assertEquals(0, triggered)

        fileSystem.addFile("file-3", byteArrayOf(1, 2, 3, 4, 5))
        assertEquals(1, triggered)
    }

    @Test
    fun addRemove() {
        fileSystem.addFile("file-1", byteArrayOf(1, 2, 3))
        assertEquals(0, triggered)
        fileSystem.addFile("file-2", byteArrayOf(1, 2, 3))
        assertEquals(0, triggered)

        fileSystem.delete("file-1")
        assertEquals(0, triggered)

        fileSystem.addFile("file-3", byteArrayOf(1, 2, 3, 4, 5))
        assertEquals(0, triggered)

        fileSystem.addFile("file-4", byteArrayOf(1, 2, 3))
        assertEquals(1, triggered)
    }

    @Test
    fun update() {
        fileSystem.addFile("file-1", byteArrayOf(1, 2, 3))
        assertEquals(0, triggered)

        val file = fileSystem.allFiles.first { it.path == "file-1" } as TestFileSystem.Entry.File
        file.content = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
        fileSystem.notify { it.fileUpdated("file-1") }

        assertEquals(1, triggered)
    }
}
