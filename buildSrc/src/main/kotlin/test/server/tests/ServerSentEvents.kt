/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server.tests

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.*

internal fun Application.serverSentEvents() {
    routing {
        route("/sse") {
            get("/hello") {
                val times = call.parameters["times"]?.toInt() ?: 1
                val events = flow {
                    repeat(times) {
                        emit(it)
                    }
                }.map {
                    SseEvent("hello\nfrom server", "hello $it", "$it")
                }
                call.respondSseEvents(events)
            }
        }
    }
}

private suspend fun ApplicationCall.respondSseEvents(events: Flow<SseEvent>) {
    respondBytesWriter(contentType = ContentType.Text.EventStream) {
        writeSseEvents(events)
    }
}

private suspend fun ByteWriteChannel.writeSseEvents(events: Flow<SseEvent>): Unit = events.collect { event ->
    if (event.id != null) {
        writeStringUtf8("id: ${event.id}\n")
    }
    if (event.event != null) {
        writeStringUtf8("event: ${event.event}\n")
    }
    for (dataLine in event.data.lines()) {
        writeStringUtf8("data: $dataLine\n")
    }
    writeStringUtf8("\n")
    flush()
}

private data class SseEvent(val data: String, val event: String? = null, val id: String? = null)
