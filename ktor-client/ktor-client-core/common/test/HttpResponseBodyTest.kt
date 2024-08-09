import io.ktor.client.statement.*
import io.ktor.utils.io.*
import kotlinx.coroutines.test.*
import kotlin.test.*

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class HttpResponseBodyTest {

    @Test
    fun streamBodyIsConsumed() = runTest {
        val expected = "Hello"
        HttpResponseBody.create(ByteReadChannel(expected)).apply {
            assertEquals(expected, readText())
            assertTrue { toChannel().isClosedForRead }
        }
    }

    @Test
    fun copyDoesNotConsume() = runTest {
        val expected = "Hello"
        HttpResponseBody.create(ByteReadChannel(expected)).apply {
            with(copy()) {
                assertEquals(expected, readText())
                assertTrue { toChannel().isClosedForRead }
            }
            assertEquals(expected, readText())
            assertTrue { copy().toChannel().isClosedForRead }
        }
    }

    @Test
    fun copyIsInvalidatedWhenOriginalIsRead() = runTest {
        val expected = "Hello"
        HttpResponseBody.create(ByteReadChannel(expected)).apply {
            val copy = copy()
            assertEquals(expected, readText())
            assertFailsWith<IllegalStateException> {
                copy.readText()
            }
        }
    }

    @Test
    fun empty() = runTest {
        HttpResponseBody.empty().apply {
            assertEquals("", readText())
            assertTrue { toChannel().isClosedForRead }
        }
    }

}
