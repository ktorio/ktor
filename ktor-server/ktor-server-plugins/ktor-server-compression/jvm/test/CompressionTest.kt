/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.testing.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import org.junit.Test
import kotlin.test.*

class CompressionTest {

    @Test
    fun gzipCompression(): Unit = runBlocking {
        val pipeline = ApplicationCallPipeline().apply {
            install(Compression) {
                conditions.add { true }
            }
        }
        val call = FakeCall().apply {
            request.headers = Headers.build { append("Accept-Encoding", "gzip") }
        }
        val result = pipeline.sendPipeline.execute(
            call,
            TextContent("test", ContentType("plain", "text"))
        ) as OutgoingContent.ReadChannelContent

        assertEquals(
            listOf(0x1f, (0x8b).toByte(), 0x8).toByteArray().toList(), // Gzip header
            result.readFrom().toByteArray().toList().subList(0, 3)
        )
    }
}
