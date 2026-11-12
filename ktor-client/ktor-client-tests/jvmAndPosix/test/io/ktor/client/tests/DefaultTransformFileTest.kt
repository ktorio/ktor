/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.tests

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.tests.utils.*
import io.ktor.test.*
import kotlinx.io.Sink
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Covers sending a file as a request body: it is streamed from disk and the source is closed once it has been sent.
 */
class DefaultTransformFileTest {

    @Test
    fun `file body is streamed to the request`() = runTest {
        withFile({ writeString("hello from a file") }) { source ->
            val sent = renderBody { setBody(source) }.toByteArray()

            assertEquals("hello from a file", sent.decodeToString())
            assertTrue(source.closed, "The file source should be closed once the body has been sent")
        }
    }

    @Test
    fun `file larger than the channel buffer is streamed in full`() = runTest {
        // The channel buffers at most 1 MiB, so a bigger file is read in several rounds
        val content = Random(42).nextBytes(2 * 1024 * 1024 + 17)

        withFile({ write(content) }) { source ->
            val sent = renderBody { setBody(source) }.toByteArray()

            assertEquals(content.size, sent.size, "The whole file should be sent")
            // Not assertContentEquals: it would print both arrays on failure
            assertTrue(content.contentEquals(sent), "The file content should be sent unchanged")
            assertTrue(source.closed, "The file source should be closed once the body has been sent")
        }
    }

    /** Writes a temporary file with [write] and runs [block] on a source over it, deleting the file afterwards. */
    @OptIn(ExperimentalUuidApi::class)
    private inline fun withFile(write: Sink.() -> Unit, block: (TrackingRawSource) -> Unit) {
        val path = Path(SystemTemporaryDirectory, "default-transform-${Uuid.random()}")
        try {
            SystemFileSystem.sink(path).buffered().use { it.write() }
            val source = TrackingRawSource(SystemFileSystem.source(path))
            try {
                block(source)
            } finally {
                source.close()
            }
        } finally {
            // Best effort: on Windows a file that is still open can't be deleted, which would mask the real failure
            runCatching { SystemFileSystem.delete(path, mustExist = false) }
        }
    }
}
