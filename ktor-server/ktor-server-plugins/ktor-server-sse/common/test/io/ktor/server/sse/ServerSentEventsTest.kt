/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.sse

import io.ktor.client.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.sse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import kotlin.test.*

class ServerSentEventsTest {

    @Test
    fun testSingleEvents() = testApplication {
        install(SSE)
        routing {
            sse("/hello") {
                send(ServerSentEvent("world", event = "send", id = "100", retry = 1000, comments = "comment"))
            }
        }

        val client = createSseClient()
        val expected = """
            data: world
            event: send
            id: 100
            retry: 1000
            : comment
            
        """.trimIndent()
        val actual = StringBuilder()
        client.sse("/hello") {
            val event = incoming.single()
            assertEquals("world", event.data)
            assertEquals("send", event.event)
            assertEquals("100", event.id)
            assertEquals(1000, event.retry)
            assertEquals("comment", event.comments)
            actual.append(event)
        }
        assertEquals(expected.lines(), actual.toString().lines())
    }

    @Test
    fun testEvents() = testApplication {
        install(SSE)
        routing {
            sse("/events") {
                repeat(100) {
                    send(ServerSentEvent("event $it"))
                }
            }
        }

        val client = createSseClient()
        client.sse("/events") {
            incoming.collectIndexed { i, event ->
                assertEquals("event $i", event.data)
            }
        }
    }

    @Test
    fun testChannelsOfEvents() = testApplication {
        install(SSE)
        routing {
            sse("/events") {
                launch {
                    repeat(100) {
                        send(ServerSentEvent("channel-1 $it"))
                    }
                }
                launch {
                    repeat(100) {
                        send(ServerSentEvent("channel-2 $it"))
                    }
                }
            }
        }

        client.get("/events").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals(ContentType.Text.EventStream.toString(), headers[HttpHeaders.ContentType])
            val events = bodyAsText().lines()
            assertEquals(401, events.size)
            for (i in 0 until 100) {
                assertContains(events, "data: channel-1 $i")
                assertContains(events, "data: channel-2 $i")
            }
        }
    }

    @Test
    fun testSeveralClients() = testApplication {
        install(SSE)
        routing {
            sse("/events") {
                repeat(100) {
                    send(ServerSentEvent("event $it"))
                }
            }
        }

        val client = createSseClient()
        client.sse("/events") {
            incoming.collectIndexed { i, event ->
                assertEquals("event $i", event.data)
            }
        }
        client.sse("/events") {
            incoming.collectIndexed { i, event ->
                assertEquals("event $i", event.data)
            }
        }
    }

    @Test
    fun testNoDuplicateHeader() = testApplication {
        install(SSE)
        routing {
            sse<String> { }
        }

        val client = createSseClient()
        client.sse<String> {
            call.response.headers.forEach { _, values ->
                assertEquals(1, values.size)
            }
        }
    }

    @Test
    fun testMultilineData() = testApplication {
        install(SSE)
        routing {
            sse("/multiline-data") {
                send(
                    """
                    First Line
                    Second Line
                    Third Line
                    """.trimIndent()
                )
            }

            sse("/one-event-data") {
                send(
                    """
                    First Line
                    
                    data: Third Line
                    """.trimIndent()
                )
            }
        }

        val client = createSseClient()

        val expectedMultilineData = """
            data: First Line
            data: Second Line
            data: Third Line

        """.trimIndent()
        val actualMultilineData = StringBuilder()
        client.sse<String>("/multiline-data") {
            incoming.collect {
                actualMultilineData.append(it.toString())
            }
        }
        assertEquals(expectedMultilineData.lines(), actualMultilineData.toString().lines())

        val expectedOneEventData = """
            data: First Line
            data: 
            data: data: Third Line
            
        """.trimIndent()
        val actualOneEventData = StringBuilder()
        client.sse<String>("/one-event-data") {
            incoming.collect {
                actualOneEventData.append(it.toString())
            }
        }
        assertEquals(expectedOneEventData.lines(), actualOneEventData.toString().lines())
    }

    class Person(val age: Int)

    @Test
    fun testSerializerInRoute() = testApplication {
        install(SSE)
        routing {
            sse("/person", serialize = { "Age ${it.age}" }) {
                repeat(10) {
                    send(Person(it))
                }
            }
        }

        val client = createSseClient()

        client.sse("/person") {
            incoming.collectIndexed { i, person ->
                assertEquals("Age $i", person.data)
            }
        }
    }

    class Person1(val age: Int)
    class Person2(val number: Int)
    class Person3(val index: Int)

    @Test
    fun testDifferentSerializers() = testApplication {
        install(SSE) {
            serialize { person: Person3 ->
                "${person.index}"
            }
        }
        routing {
            sse("/person1", serialize = { println("1");"${it.age}" }) {
                send(Person1(22))
            }
            sse("/person2", serialize = { "${it.number}" }) {
                send(Person2(123456))
            }
            sse("/person3") {
                send(Person3(0))
            }
        }

        val client = createSseClient()
        client.sse("/person1") {
            incoming.single().apply {
                assertEquals("22", data)
            }
        }
        client.sse("/person2") {
            incoming.single().apply {
                assertEquals("123456", data)
            }
        }
        client.sse("/person3") {
            incoming.single().apply {
                assertEquals("0", data)
            }
        }
    }

    @Serializable
    data class Customer(val id: Int, val firstName: String, val lastName: String)

    @Test
    fun testJsonDeserializer() = testApplication {
        install(SSE) {
            serialize<Customer> {
                Json.encodeToString(it)
            }
        }
        routing {
            sse("/json") {
                send(Customer(0, "Jet", "Brains"))
            }
        }

        assertEquals(
            "data: {\"id\":0,\"firstName\":\"Jet\",\"lastName\":\"Brains\"}",
            client.get("/json").bodyAsText().trim()
        )
    }

    private fun ApplicationTestBuilder.createSseClient(): HttpClient {
        val client = createClient {
            install(io.ktor.client.plugins.sse.SSE)
        }
        return client
    }
}
