/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test.server.tests

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

internal fun Application.serverSentEvents() {
    install(SSE)
    routing {
        route("/sse") {
            sse("/hello") {
                val delayMillis = call.parameters["delay"]?.toLong() ?: 0
                delay(delayMillis)

                val times = call.parameters["times"]?.toInt() ?: 1
                repeat(times) {
                    send("hello\nfrom server", "hello $it", "$it")
                }
            }
            get("/comments") {
                val times = call.parameters["times"]?.toInt() ?: 1
                var isComment = false
                val events = flow {
                    repeat(times * 2) {
                        emit(it)
                    }
                }.map {
                    isComment = !isComment
                    if (isComment) {
                        SseEvent(comments = "$it")
                    } else {
                        SseEvent(data = "$it")
                    }
                }
                call.respondSseEvents(events)
            }
            get("/auth") {
                val token = call.request.headers["Authorization"]
                if (token.isNullOrEmpty() || token.contains("invalid")) {
                    call.response.header(HttpHeaders.WWWAuthenticate, "Bearer realm=\"TestServer\"")
                    call.respond(HttpStatusCode.Unauthorized)
                    return@get
                }

                call.respondSseEvents(
                    flow {
                        emit(SseEvent("hello after refresh"))
                    }
                )
            }
            get("/content_type_with_charset") {
                val events = flow {
                    emit(SseEvent("hello\nfrom server", "hello 0", "0"))
                }
                val contentType = ContentType.Text.EventStream.withCharset(Charsets.UTF_8)
                call.respondBytesWriter(contentType = contentType) {
                    writeSseEvents(events)
                }
            }
            get("/echo") {
                call.respondSseEvents(
                    flow {
                        emit(SseEvent(call.receiveText()))
                    }
                )
            }

            post {
                call.respondSseEvents(
                    flow {
                        emit(SseEvent("Hello"))
                    }
                )
            }

            get("/content-type-text-plain") {
                call.response.header(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                call.respond(HttpStatusCode.OK)
            }

            get("/person") {
                val times = call.parameters["times"]?.toInt() ?: 1
                call.respondSseEvents(
                    flow {
                        repeat(times) {
                            emit(SseEvent(data = "Name $it", event = "event $it", id = "$it"))
                        }
                    }
                )
            }

            get("/json") {
                val customer = """
                               { "id": 1, 
                                 "firstName": "Jet", 
                                 "lastName": "Brains" 
                               }""".trimIndent()
                val product = """
                              { "name": "Milk", 
                                "price": "100"
                              }""".trimIndent()
                call.respondSseEvents(
                    flow {
                        emit(SseEvent(data = customer))
                        emit(SseEvent(data = product))
                    }
                )
            }
            sse("/reconnection") {
                val count = call.parameters["count"]?.toInt() ?: 0
                val lastEventId = call.request.header("Last-Event-ID")?.toInt() ?: 0
                (1..count).forEach {
                    val currentId = lastEventId + it
                    send(id = "$currentId")
                }
            }
            var countOfReconnections = 0
            get("exception-on-reconnection") {
                val count = call.parameters["count"]?.toInt() ?: 0
                val maxCountOfReconnections = call.parameters["count-of-reconnections"]?.toInt() ?: -1
                val lastEventId = call.request.header("Last-Event-ID")
                call.response.headers.append("MY-HEADER", "$countOfReconnections")
                countOfReconnections++
                if (lastEventId == null || countOfReconnections == maxCountOfReconnections) {
                    countOfReconnections = 0
                    call.respondSseEvents(
                        flow {
                            repeat(count) {
                                emit(SseEvent(id = "$it"))
                            }
                        }
                    )
                } else {
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }
            get("no-content") {
                call.respond(HttpStatusCode.NoContent)
            }
            get("no-content-after-reconnection") {
                val count = call.parameters["count"]?.toInt() ?: 0
                val lastEventId = call.request.header("Last-Event-ID")
                if (lastEventId == null) {
                    call.respondSseEvents(
                        flow {
                            repeat(count) {
                                emit(SseEvent(id = "$it"))
                            }
                        }
                    )
                } else {
                    call.respond(HttpStatusCode.NoContent)
                }
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
        writeStringUtf8WithNewlineAndFlush("id: ${event.id}")
    }
    if (event.event != null) {
        writeStringUtf8WithNewlineAndFlush("event: ${event.event}")
    }
    if (event.data != null) {
        for (dataLine in event.data.lines()) {
            writeStringUtf8WithNewlineAndFlush("data: $dataLine")
        }
    }
    if (event.retry != null) {
        writeStringUtf8WithNewlineAndFlush("retry: ${event.retry}")
    }

    if (event.comments != null) {
        for (dataLine in event.comments.lines()) {
            writeStringUtf8WithNewlineAndFlush(": $dataLine")
        }
    }
    writeStringUtf8WithNewlineAndFlush()
}

private suspend fun ByteWriteChannel.writeStringUtf8WithNewlineAndFlush(data: String? = null) {
    if (data != null) {
        writeStringUtf8(data)
    }
    writeStringUtf8("\n")
    flush()
}

private data class SseEvent(
    val data: String? = null,
    val event: String? = null,
    val id: String? = null,
    val retry: Long? = null,
    val comments: String? = null
)
